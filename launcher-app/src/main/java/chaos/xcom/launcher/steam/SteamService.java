package chaos.xcom.launcher.steam;

import chaos.db.gen.tables.records.SteamModRecord;
import chaos.xcom.launcher.common.JsonConverter;
import chaos.xcom.launcher.db.property.DbProperties;
import chaos.xcom.launcher.event.EventPublisher;
import chaos.xcom.launcher.exception.InternalException;
import chaos.xcom.launcher.steam.SteamMod.SteamRequiredMod;
import chaos.xcom.launcher.swing.SwingService;
import chaos.xcom.launcher.util.OsUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class SteamService {

    /**
     * How many time to skip processing new requests after receiving a 429 Too Many Requests error from Steam.
     */
    private static final int TOO_MANY_REQUESTS_SKIP_TIMEOUT_MS = 5000;
    private static Instant LAST_STEAM_TOO_MANY_REQUESTS_ERROR_AT = Instant.now().minusSeconds(600000);
    private static Instant LAST_STEAM_REQUEST_FINISHED_AT = Instant.now().minusSeconds(600000);

    private static Pattern MOD_ID_PATTERN = Pattern.compile("/filedetails/\\?id=(\\d+)");

    private final EventPublisher eventPublisher;

    // Tracks uniqueness
    private final Set<String> pendingStreamIds = ConcurrentHashMap.newKeySet();

    private final AtomicInteger errorsCount = new AtomicInteger(0);
    private final AtomicInteger totalProcessedItems = new AtomicInteger(0);

    // Queue for processing
    private final BlockingQueue<String> queueSteamIds = new LinkedBlockingQueue<>();
    private final SteamModRepository steamModRepository;
    private final JsonConverter jsonConverter;
    private final DbProperties dbProperties;

    @PostConstruct
    // Start a virtual thread to process items
    public void init() {
        Thread.ofVirtual().start(() -> {
            while (true) {
                String steamModId = null;
                try {
                    steamModId = null;
                    publishSteamSyncProgress();
                    steamModId = queueSteamIds.take(); // blocks until an item is available
                    publishSteamSyncProgress();
                    if (!processSteamMod(steamModId)) {
                        errorsCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorsCount.incrementAndGet();
                    log.error("Error processing steam mod {}", steamModId, e);
                } finally {
                    if (steamModId != null) {
                        pendingStreamIds.remove(steamModId);
                    }
                    if (queueSteamIds.isEmpty()) {
                        if (errorsCount.intValue() > 0) {
                            int finalErrorsCount = errorsCount.intValue();
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(SwingService.getLastActiveWindowBounds(),
                                        "Failed to sync " + finalErrorsCount + " mods.\n"
                                                + "Please try again later.",
                                        "Steam sync error",
                                        JOptionPane.ERROR_MESSAGE);
                            });
                        }
                        errorsCount.set(0);
                        totalProcessedItems.set(0);
                    } else {
                        totalProcessedItems.incrementAndGet();
                    }
                }
            }
        });
    }

    public static void openSteamModInBrowser(String steamModId) {
        openSteamModsInBrowser(List.of(steamModId));
    }

    public static void openSteamModsInBrowser(Collection<String> steamModIds) {
        try {
            for (String steamModId : steamModIds) {
                if (steamModId != null) {
                    Desktop.getDesktop().browse(new URI("https://steamcommunity.com/sharedfiles/filedetails/?id=" + steamModId));
                }
            }
        } catch (Exception ex) {
            throw new InternalException().cause(ex);
        }
    }

    private void publishSteamSyncProgress() {
        int processedCount = totalProcessedItems.get();
        int totalCount = processedCount + pendingStreamIds.size();
        eventPublisher.publishAsync(new SteamSyncProgress(processedCount, totalCount));
    }

    public HashMap<String, SteamMod> findAllSteamMods() {
        Map<String, SteamModRecord> steamModDbMap = steamModRepository.findAll().stream()
                .collect(Collectors.toMap(SteamModRecord::getId, v -> v));
        HashMap<String, SteamMod> result = new HashMap<>(steamModDbMap.size());
        for (SteamModRecord dbRecord : steamModDbMap.values()) {
            SteamMod steamMod = new SteamMod();
            steamMod.setSteamModId(dbRecord.getId());
            steamMod.setSteamModName(dbRecord.getTitle());
            steamMod.setDescription(dbRecord.getDescription());
            steamMod.setUpdatedAt(dbRecord.getUpdatedAt());
            try {
                List<String> requiredModIds = jsonConverter.parse(dbRecord.getRequiredSteamModIds(), new TypeReference<>() {});
                if (requiredModIds != null) {
                    for (String requiredModId : requiredModIds) {
                        SteamRequiredMod requiredMod = new SteamRequiredMod();
                        requiredMod.setSteamModId(requiredModId);
                        SteamModRecord requiredDbRecord = steamModDbMap.get(requiredModId);
                        requiredMod.setSteamModName(requiredDbRecord.getTitle());
                        steamMod.getRequiredSteamMods().add(requiredMod);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing steam mod {}", dbRecord.getId(), e);
            }
            result.put(steamMod.getSteamModId(), steamMod);
        }
        return result;
    }

    public void SyncSteamModsInAsync(List<String> steamModIds) {
        if (steamModIds.isEmpty()) {
            return;
        }
        log.info("Sync steam mods: {}", steamModIds.size() <= 10 ? steamModIds : steamModIds.size());
        for (String steamModId : steamModIds) {
            appendSteamModForSync(steamModId);
        }
    }

    /**
     * Process a single steam mod
     * @return true if processed successfully, false otherwise.
     */
    private boolean processSteamMod(String steamModId) {
        SteamMod steamMod = parseSteamMod(steamModId).orElse(null);
        if (steamMod == null) {
            log.error("Failed to parse steam mod {}", steamModId);
            return false;
        } else {
            SteamModRecord dbSteamMod = new SteamModRecord();
            dbSteamMod.setId(steamMod.getSteamModId());
            dbSteamMod.setTitle(steamMod.getSteamModName());
            dbSteamMod.setDescription(steamMod.getDescription());
            dbSteamMod.setUpdatedAt(steamMod.getUpdatedAt());
            List<String> requiredModIds = steamMod.getRequiredSteamMods().stream().map(SteamRequiredMod::getSteamModId).toList();
            dbSteamMod.setRequiredSteamModIds(jsonConverter.toJson(requiredModIds));
            steamModRepository.save(dbSteamMod);

            steamModRepository.saveTitle(steamMod.getRequiredSteamMods().stream().map(v -> {
                SteamModRecord requiredSteamMod = new SteamModRecord();
                requiredSteamMod.setId(v.getSteamModId());
                requiredSteamMod.setTitle(v.getSteamModName());
                return requiredSteamMod;
            }).toList());

            eventPublisher.publishAsync(steamMod);
            return true;
        }
    }

    private void appendSteamModForSync(String steamModId) {
        if (pendingStreamIds.add(steamModId)) {
            try {
                queueSteamIds.add(steamModId);
            } catch (Exception e) {
                log.error("Failed to enqueue steam mod for sync: {}", steamModId, e);
                pendingStreamIds.remove(steamModId);
            }
        }
    }

    public static String formatModPageUrl(String modId) {
        return "https://steamcommunity.com/workshop/filedetails/?id=" + modId;
    }

    public Optional<SteamMod> parseSteamMod(String steamModId) {
        try {
            long startTime = System.currentTimeMillis();
            if (startTime - LAST_STEAM_TOO_MANY_REQUESTS_ERROR_AT.toEpochMilli() < TOO_MANY_REQUESTS_SKIP_TIMEOUT_MS) {
                log.warn("Skipping steam mod parsing due to recent 429 Too Many Requests error: {}", steamModId);
                return Optional.empty();
            }
            int requestDelayMs = dbProperties.steamRequestDelaySec.get() * 1000;
            if (startTime - LAST_STEAM_REQUEST_FINISHED_AT.toEpochMilli() < requestDelayMs) {
                long sleepTime = requestDelayMs - (startTime - LAST_STEAM_REQUEST_FINISHED_AT.toEpochMilli());
                log.info("Sleeping {} ms before next steam request", sleepTime);
                Thread.sleep(sleepTime);
            }
            Document doc = Jsoup.connect(formatModPageUrl(steamModId))
                    .userAgent("Mozilla/5.0 (Wayland; Linux x86_64)")
                    .timeout(30_000)
                    .get();

            Element titleEl = doc.selectFirst(".workshopItemTitle");
            if (titleEl == null) {
                log.error("Failed to find mod title element on steam mod page: {}", steamModId);
                return Optional.empty();
            }
            Element descriptionEl = doc.selectFirst(".workshopItemDescription");
            String description = descriptionEl == null ? null : descriptionEl.toString().strip();

            SteamMod steamMod = new SteamMod();
            steamMod.setSteamModId(steamModId);
            steamMod.setDescription(description);
            steamMod.setSteamModName(titleEl.text().strip());
            steamMod.setUpdatedAt(Instant.now());
            // preferred selectors to cover different page variants
            List<String> selectors = Arrays.asList(
                    ".requiredItemsContainer a",
                    ".required_items_area a",
                    ".required_item_area a",
                    "#RequiredItems a"
            );

            for (String sel : selectors) {
                Elements links = doc.select(sel);
                if (!links.isEmpty()) {
                    for (Element a : links) {
                        String href = a.attr("abs:href").strip();

                        // try to get inner displayed name (often inside a div.requiredItem)
                        String name = a.selectFirst(".requiredItem") != null
                                ? a.selectFirst(".requiredItem").text().trim()
                                : a.text().strip();
                        if (!name.isEmpty() && !href.isEmpty()) {
                            Matcher matcher = MOD_ID_PATTERN.matcher(href);
                            if (!matcher.find()) {
                                continue;
                            }
                            String requiredModId = matcher.group(1).strip(); // returns the numeric ID

                            SteamRequiredMod requiredMod = new SteamRequiredMod();
                            requiredMod.setSteamModId(requiredModId);
                            requiredMod.setSteamModName(name);
                            steamMod.getRequiredSteamMods().add(requiredMod);
                        }
                    }
                    break; // found a matching container, stop trying other selectors
                }
            }

            long endTime = System.currentTimeMillis();
            log.info("Parsed steam mod {} in {} ms, found {} required items",
                    steamMod,
                    (endTime - startTime),
                    steamMod.getRequiredSteamMods().size());
            return Optional.of(steamMod);
        } catch (HttpStatusException e) {
            if (e.getStatusCode() == 429) {
                LAST_STEAM_TOO_MANY_REQUESTS_ERROR_AT = Instant.now();
                log.warn("Steam mod page returned HTTP 429 (Too Many Requests): {}", steamModId);
            } else {
                log.error("Failed to fetch steam mod page (HTTP {}): {}", e.getStatusCode(), steamModId);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to parse steam mod page: {}", steamModId, e);
            return Optional.empty();
        } finally {
            LAST_STEAM_REQUEST_FINISHED_AT = Instant.now();
        }
    }

    public static Optional<File> findXcomGameExeDirectory() {
        if (OsUtils.IS_WINDOWS) {
            try {
                Process process = new ProcessBuilder("reg", "query",
                        "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath").start();

                LineIterator iterator = IOUtils.lineIterator(process.getInputStream(), StandardCharsets.UTF_8);
                while (iterator.hasNext()) {
                    String line = iterator.next().trim();
                    if (line.contains("SteamPath")) {
                        String path = line.split("    ")[line.split("    ").length - 1].trim();
                        log.info("Found SteamPath: {}", path);
                        String exeDir = path + "/steamapps/common/XCOM 2/XCom2-WarOfTheChosen/Binaries/Win64";
                        return Optional.of(new File(exeDir));
                    }
                }
            } catch (Exception e) {
                log.error("Failed to find Steam exe directory", e);
            }
        }
        return Optional.empty();
    }

}
