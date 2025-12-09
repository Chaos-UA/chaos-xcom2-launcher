package chaos.xcom.launcher.steam;

import chaos.db.gen.tables.records.SteamModRecord;
import chaos.xcom.launcher.common.JsonConverter;
import chaos.xcom.launcher.event.EventPublisher;
import chaos.xcom.launcher.steam.SteamMod.SteamRequiredMod;
import chaos.xcom.launcher.util.OsUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
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

    private static Pattern MOD_ID_PATTERN = Pattern.compile("/filedetails/\\?id=(\\d+)");

    private final EventPublisher eventPublisher;

    // Tracks uniqueness
    private final Set<String> pendingStreamIds = ConcurrentHashMap.newKeySet();

    private final AtomicInteger totalProcessedItems = new AtomicInteger(0);

    // Queue for processing
    private final BlockingQueue<String> queueSteamIds = new LinkedBlockingQueue<>();
    private final SteamModRepository steamModRepository;
    private final JsonConverter jsonConverter;

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
                    processSteamMod(steamModId);
                } catch (Exception e) {
                    log.error("Error processing steam mod {}", steamModId, e);
                } finally {
                    if (steamModId != null) {
                        pendingStreamIds.remove(steamModId);
                    }
                    if (queueSteamIds.isEmpty()) {
                        totalProcessedItems.set(0);
                    } else {
                        totalProcessedItems.incrementAndGet();
                    }
                }
            }
        });
    }

    private void publishSteamSyncProgress() {
        int processedCount = totalProcessedItems.get();
        int totalCount = processedCount + pendingStreamIds.size();
        eventPublisher.publishAsync(new SteamSyncProgress(processedCount, totalCount));
    }

    public Map<String, SteamMod> findAllSteamMods() {
        Map<String, SteamModRecord> steamModDbMap = steamModRepository.findAll().stream()
                .collect(Collectors.toMap(SteamModRecord::getId, v -> v));
        Map<String, SteamMod> result = new HashMap<>(steamModDbMap.size());
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

    private void processSteamMod(String steamModId) {
        SteamMod steamMod = parseSteamMod(steamModId).orElse(null);
        if (steamMod == null) {
            log.error("Failed to parse steam mod {}", steamModId);
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

    public static Optional<SteamMod> parseSteamMod(String steamModId) {
        try {
            long startTime = System.currentTimeMillis();
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
        } catch (Exception e) {
            log.error("Failed to parse steam mod page: {}", steamModId, e);
            return Optional.empty();
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
