package chaos.xcom.launcher.steam;

import chaos.db.gen.tables.records.SteamModRecord;
import chaos.xcom.launcher.common.JsonConverter;
import chaos.xcom.launcher.db.property.DbProperties;
import chaos.xcom.launcher.event.EventPublisher;
import chaos.xcom.launcher.exception.InternalException;
import chaos.xcom.launcher.steam.SteamMod.SteamRequiredMod;
import chaos.xcom.launcher.swing.SwingService;
import chaos.xcom.launcher.util.Longs;
import chaos.xcom.launcher.util.OsUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.jooq.Field;
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

import static chaos.db.gen.tables.SteamMod.STEAM_MOD;

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
    private final SteamClient steamClient;

    private volatile boolean steamModsDownloadInProgress = false;

    // Tracks uniqueness
    private final Set<Long> pendingStreamIds = ConcurrentHashMap.newKeySet();

    private final AtomicInteger errorsCount = new AtomicInteger(0);
    private final AtomicInteger totalProcessedItems = new AtomicInteger(0);

    // Queue for processing
    private final BlockingQueue<Long> queueSteamIds = new LinkedBlockingQueue<>();
    private final SteamModRepository steamModRepository;
    private final JsonConverter jsonConverter;
    private final DbProperties dbProperties;

    @PostConstruct
    // Start a virtual thread to process items
    public void init() {
        Thread.ofVirtual().start(() -> {
            while (true) {
                Long steamModId = null;
                try {
                    steamModId = null;
                    publishSteamSyncProgress(null);
                    steamModId = queueSteamIds.take(); // blocks until an item is available
                    publishSteamSyncProgress(steamModId);
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

    public static void openSteamModInBrowser(Long steamModId) {
        openSteamModsInBrowser(List.of(steamModId));
    }

    public static void openSteamModsInBrowser(Collection<Long> steamModIds) {
        try {
            for (Long steamModId : steamModIds) {
                if (steamModId != null) {
                    Desktop.getDesktop().browse(new URI("https://steamcommunity.com/sharedfiles/filedetails/?id=" + steamModId));
                }
            }
        } catch (Exception ex) {
            throw new InternalException().cause(ex);
        }
    }

    private void publishSteamSyncProgress(Long steamModId) {
        int processedCount = totalProcessedItems.get();
        int totalCount = processedCount + pendingStreamIds.size();
        eventPublisher.publishAsync(new SteamSyncProgress(processedCount, totalCount, steamModId));
    }

    public HashMap<Long, SteamMod> findAllSteamMods() {
        Map<Long, SteamModRecord> steamModDbMap = steamModRepository.findAll().stream()
                .collect(Collectors.toMap(v -> Long.parseLong(v.getId()), v -> v));
        HashMap<Long, SteamMod> result = new HashMap<>(steamModDbMap.size());
        for (SteamModRecord dbRecord : steamModDbMap.values()) {
            SteamMod steamMod = new SteamMod();
            steamMod.setSteamModId(Long.parseLong(dbRecord.getId()));
            steamMod.setSteamModName(dbRecord.getTitle());
            steamMod.setDescription(dbRecord.getDescription());
            steamMod.setSyncedAt(dbRecord.getUpdatedAt());
            steamMod.setLastUpdatedAt(dbRecord.getLastUpdatedAt());
            steamMod.setLastDownloadedAt(dbRecord.getLastDownloadAt());
            try {
                List<Long> requiredModIds = jsonConverter.parse(dbRecord.getRequiredSteamModIds(), new TypeReference<>() {});
                if (requiredModIds != null) {
                    for (Long requiredModId : requiredModIds) {
                        SteamRequiredMod requiredMod = new SteamRequiredMod();
                        requiredMod.setSteamModId(requiredModId);
                        SteamModRecord requiredDbRecord = steamModDbMap.get(requiredModId);
                        if (requiredDbRecord != null) {
                            requiredMod.setSteamModName(requiredDbRecord.getTitle());
                        }
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

    public void syncSteamModsInAsync(List<Long> steamModIds) {
        if (steamModIds.isEmpty()) {
            return;
        }
        if (steamClient.isWorking()) {
            Thread.ofVirtual().start(() -> {
                log.info("Sync steam mods: {}", steamModIds.size() <= 10 ? steamModIds : steamModIds.size());
                try {
                    syncSteamModsViaSteamApi(steamModIds);
                } catch (Exception e) {
                    log.error("Error processing steam mods {}. Fallback to parsing", steamModIds.size(), e);
                    syncSteamModsViaHtmlParsingAsync(steamModIds);
                }
            });
        } else {
            syncSteamModsViaHtmlParsingAsync(steamModIds);
        }
    }

    private SteamModRecord toDbRecordWithNoLastDownloadAt(SteamClient.SteamModQueryResult result) {
        SteamModRecord dbSteamMod = new SteamModRecord();
        dbSteamMod.setId(Longs.toString(result.getSteamModId()));
        dbSteamMod.setTitle(result.getTitle());
        dbSteamMod.setDescription(steamBBToHtml(result.getDescription()));
        dbSteamMod.setUpdatedAt(Instant.now());
        dbSteamMod.setLastUpdatedAt(result.getUpdatedAt());
        dbSteamMod.setRequiredSteamModIds(jsonConverter.toJson(result.getChildIds()));
        return dbSteamMod;
    }

    public void syncSteamModsViaSteamApi(List<Long> steamModIds) {
        long startTime = System.currentTimeMillis();
        int maxBatchSize = SteamClient.MAX_PAGE_SIZE;

        // Loop through the entire list, stepping by 1000 each time
        HashSet<Long> syncedIds = new HashSet<>();
        HashSet<Long> childIds = new HashSet<>();
        int count = 0;
        eventPublisher.publishAsync(new SteamSyncProgress(count, steamModIds.size()));
        Set<Field<?>> saveOrUpdateFields = Set.of(STEAM_MOD.TITLE, STEAM_MOD.DESCRIPTION,
                STEAM_MOD.UPDATED_AT, STEAM_MOD.LAST_UPDATED_AT, STEAM_MOD.REQUIRED_STEAM_MOD_IDS);
        try {
            for (int i = 0; i < steamModIds.size(); i += maxBatchSize) {

                // 1. Safely slice the list. Math.min prevents IndexOutOfBounds on the final, smaller chunk
                int endIndex = Math.min(i + maxBatchSize, steamModIds.size());
                List<Long> batchIds = steamModIds.subList(i, endIndex);

                // 2. Fetch data from Steam API for just this batch
                List<SteamClient.SteamModQueryResult> results = steamClient.getSteamModsInfo(batchIds, true, true).get();

                // 3. Convert to database records
                List<SteamModRecord> records = results.stream().map(this::toDbRecordWithNoLastDownloadAt).toList();

                // 4. Save this specific batch to the database
                steamModRepository.saveIncluding(records, saveOrUpdateFields);
                syncedIds.addAll(batchIds);
                for (SteamClient.SteamModQueryResult result : results) {
                    childIds.addAll(result.getChildIds());
                }

                count += steamModIds.size();
                int totalCount = steamModIds.size();
                eventPublisher.publishAsync(new SteamSyncProgress(count, totalCount));
                log.info("Processed steam mods batch: {} / {}", count, totalCount);
            }

            Set<Long> childIdsToSync = childIds.stream().filter(id -> !syncedIds.contains(id))
                    .collect(Collectors.toSet());
            if (childIdsToSync.size() > 0) {
                int processedCount = 0;
                int totalCount = childIdsToSync.size();
                eventPublisher.publishAsync(new SteamSyncProgress(processedCount, totalCount));

                // 2. Fetch data from Steam API for just this batch
                List<SteamClient.SteamModQueryResult> results = steamClient.getSteamModsInfo(childIdsToSync, false, true).get();

                // 3. Convert to database records
                List<SteamModRecord> records = results.stream().map(this::toDbRecordWithNoLastDownloadAt).toList();

                // 4. Save this specific batch to the database
                steamModRepository.saveIncluding(records, saveOrUpdateFields);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to sync mods via Steam API", e);
        }
        eventPublisher.publishAsync(new SteamSyncProgress(0, 0));
        log.info("Finished processing steam mods {} in {} ms", steamModIds.size(), System.currentTimeMillis() - startTime);
    }

    private void syncSteamModsViaHtmlParsingAsync(Collection<Long> steamModIds) {
        for (Long steamModId : steamModIds) {
            appendSteamModForSync(steamModId);
        }
    }

    /**
     * Process a single steam mod
     * @return true if processed successfully, false otherwise.
     */
    private boolean processSteamMod(Long steamModId) {
        SteamMod steamMod = parseSteamModHtml(steamModId).orElse(null);
        if (steamMod == null) {
            log.error("Failed to parse steam mod {}", steamModId);
            return false;
        } else {
            SteamModRecord dbSteamMod = new SteamModRecord();
            dbSteamMod.setId(Longs.toString(steamMod.getSteamModId()));
            dbSteamMod.setTitle(steamMod.getSteamModName());
            dbSteamMod.setDescription(steamMod.getDescription());
            dbSteamMod.setUpdatedAt(steamMod.getSyncedAt());
            List<Long> requiredModIds = steamMod.getRequiredSteamMods().stream().map(SteamRequiredMod::getSteamModId).toList();
            dbSteamMod.setRequiredSteamModIds(jsonConverter.toJson(requiredModIds));
            steamModRepository.saveIncluding(List.of(dbSteamMod),
                    Set.of(STEAM_MOD.TITLE, STEAM_MOD.DESCRIPTION, STEAM_MOD.UPDATED_AT,
                            STEAM_MOD.REQUIRED_STEAM_MOD_IDS));

            steamModRepository.saveIncluding(steamMod.getRequiredSteamMods().stream().map(v -> {
                SteamModRecord requiredSteamMod = new SteamModRecord();
                requiredSteamMod.setId(Longs.toString(v.getSteamModId()));
                requiredSteamMod.setTitle(v.getSteamModName());
                return requiredSteamMod;
            }).toList(), Set.of(STEAM_MOD.TITLE)); // on conflict update only title

            eventPublisher.publishAsync(steamMod);
            return true;
        }
    }

    private void appendSteamModForSync(Long steamModId) {
        if (pendingStreamIds.add(steamModId)) {
            try {
                queueSteamIds.add(steamModId);
            } catch (Exception e) {
                log.error("Failed to enqueue steam mod for sync: {}", steamModId, e);
                pendingStreamIds.remove(steamModId);
            }
        }
    }

    public static String formatModPageUrl(long modId) {
        return "https://steamcommunity.com/workshop/filedetails/?id=" + modId;
    }

    Optional<SteamMod> parseSteamModHtml(Long steamModId) {
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
            steamMod.setSyncedAt(Instant.now());
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
                            requiredMod.setSteamModId(Longs.parseLong(requiredModId));
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

    public static String steamBBToHtml(String bbcode) {
        if (bbcode == null || bbcode.isEmpty()) {
            return bbcode;
        }

        String html = bbcode;

        // 1. Headers (Added 's' flag to support multiline headers)
        html = html.replaceAll("(?is)\\[h1\\](.*?)\\[/h1\\]", "<h1>$1</h1>");
        html = html.replaceAll("(?is)\\[h2\\](.*?)\\[/h2\\]", "<h2>$1</h2>");
        html = html.replaceAll("(?is)\\[h3\\](.*?)\\[/h3\\]", "<h3>$1</h3>");

        // 2. Text Formatting (Added 's' flag so formatting works across line breaks)
        html = html.replaceAll("(?is)\\[b\\](.*?)\\[/b\\]", "<strong>$1</strong>");
        html = html.replaceAll("(?is)\\[i\\](.*?)\\[/i\\]", "<em>$1</em>");
        html = html.replaceAll("(?is)\\[u\\](.*?)\\[/u\\]", "<u>$1</u>");
        html = html.replaceAll("(?is)\\[strike\\](.*?)\\[/strike\\]", "<del>$1</del>");
        html = html.replaceAll("(?is)\\[spoiler\\](.*?)\\[/spoiler\\]", "<span class=\"spoiler\">$1</span>");

        // 3. Quotes
        html = html.replaceAll("(?is)\\[quote=(.*?)\\](.*?)\\[/quote\\]", "<blockquote><strong>$1:</strong><br>$2</blockquote>");
        html = html.replaceAll("(?is)\\[quote\\](.*?)\\[/quote\\]", "<blockquote>$1</blockquote>");

        // 4. Code Blocks & Horizontal Rules
        html = html.replaceAll("(?is)\\[code\\](.*?)\\[/code\\]", "<pre><code>$1</code></pre>");
        html = html.replaceAll("(?i)\\[hr\\]\\[/hr\\]", "<hr>");

        // 5. Links / URLs
        html = html.replaceAll("(?i)\\[url=(.*?)\\](.*?)\\[/url\\]", "<a href=\"$1\" target=\"_blank\" rel=\"noopener noreferrer\">$2</a>");
        html = html.replaceAll("(?i)\\[url\\](.*?)\\[/url\\]", "<a href=\"$1\" target=\"_blank\" rel=\"noopener noreferrer\">$1</a>");

        // 6. Images
        html = html.replaceAll("(?i)\\[img\\](.*?)\\[/img\\]", "<img src=\"$1\" alt=\"Steam Image\" style=\"max-width: 100%; height: auto;\">");

        // 7. Lists
        html = html.replaceAll("(?is)\\[\\*\\](.*?)(?=\\[\\*\\]|\\[/list\\])", "<li>$1</li>");
        html = html.replaceAll("(?i)\\[list\\]", "<ul>");
        html = html.replaceAll("(?i)\\[/list\\]", "</ul>");

        // 8. Line Breaks
        // Знаходить 2 або більше натискань Enter і жорстко обрізає їх до двох (\n\n).
        // Це залишить рівно один красивий пустий рядок між абзацами.
        html = html.replaceAll("(\\r?\\n[ \\t]*){2,}", "\n\n");
        html = html.replaceAll("\r\n|\n", "<br>");

        return html.trim();
    }

    public boolean maybeShowDownloadInProgressDialog() {
        if (steamModsDownloadInProgress) {
            JOptionPane.showMessageDialog(SwingService.getLastActiveWindowBounds(),
                    "Download already in progress", "Already downloading", JOptionPane.INFORMATION_MESSAGE);
            return true;
        }
        return false;
    }

    public void downloadSteamModsAsync(Collection<Long> steamModIds, Runnable onFinish) {
        Set<Long> steamModIdsToDownload = new LinkedHashSet<>(steamModIds);
        if (maybeShowDownloadInProgressDialog()) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                steamModsDownloadInProgress = true;
                log.info("Downloading steam mods: {}", steamModIdsToDownload.size());

                int[] retryDelaysMs = {3000, 10000, 30000};

                int i = 0;
                for (long steamModId : steamModIdsToDownload) {
                    eventPublisher.publishAsync(new SteamSyncProgress(i++, steamModIdsToDownload.size(), steamModId));

                    boolean success = false;
                    int attempt = 0;

                    // Keep trying as long as we haven't succeeded AND we haven't exhausted our retries
                    // attempt 0 = initial try, attempt 1 = 3s delay, attempt 2 = 5s delay
                    while (!success && attempt <= retryDelaysMs.length) {
                        try {
                            // Attempt the download
                            steamClient.downloadSteamMod(steamModId, true).get();
                            success = true; // If we get here, it succeeded. Break out of the while loop.

                        } catch (Exception e) {
                            attempt++;

                            if (attempt <= retryDelaysMs.length) {
                                int currentDelay = retryDelaysMs[attempt - 1];
                                log.error("Error downloading mod " + steamModId + ". Retrying in " + (currentDelay / 1000) + " seconds... (Retry " + attempt + ")");

                                Thread.sleep(currentDelay);
                            } else {
                                log.error("Failed to download mod " + steamModId + " after all retries. " + e.getMessage());
                                throw e;
                            }
                        }
                    }
                    steamModRepository.updateDownloadedAt(steamModId, Instant.now());
                    eventPublisher.publish(new SteamModDownloadedEvent(steamModId));
                }
                eventPublisher.publishAsync(new SteamSyncProgress(0, 0));
                log.info("Finished downloading steam mods: {}", steamModIdsToDownload.size());
            } catch (Exception e) {
                log.error("Error downloading steam mods {}", steamModIdsToDownload.size(), e);
                JOptionPane.showMessageDialog(SwingService.getLastActiveWindowBounds(),
                        "Failed to download/update mods\n" + e.getMessage(), "Error downloading steam mods", JOptionPane.ERROR_MESSAGE);
            } finally {
                steamModsDownloadInProgress = false;
                onFinish.run();
            }
        });

    }

    public void subscribeSteamMods(List<Long> steamModIdsToSubscribe) {
        for (long steamModId : new HashSet<>(steamModIdsToSubscribe)) {
            try {
                steamClient.subscribeSteamMod(steamModId).get();
                log.info("Subscribed to steam mod {}", steamModId);
            } catch (Exception e) {
                log.error("Error subscribing to steam mod {}", steamModId, e);
            }
        }
    }

    public void unsubscribeSteamMods(List<Long> steamModIdsToUnsubscribe) {
        for (long steamModId : new HashSet<>(steamModIdsToUnsubscribe)) {
            try {
                steamClient.unsubscribeSteamMod(steamModId).get();
                log.info("Unsubscribed from steam mod {}", steamModId);
            } catch (Exception e) {
                log.error("Error unsubscribing from steam mod {}", steamModId, e);
            }
        }
    }

    public void eraseLastDownloadedAtForAll() {
        steamModRepository.eraseLastDownloadedAtForAll();
    }
}
