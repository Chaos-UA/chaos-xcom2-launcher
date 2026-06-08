package chaos.xcom.launcher.steam;

import com.codedisaster.steamworks.*;
import jakarta.inject.Singleton;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Slf4j
public class SteamClient {

    public static final int MAX_PAGE_SIZE = 1000;
    public static final int XCOM2_APP_ID = 268500;
    private static final int MS_DELAY_BEFORE_STEAM_API_SHUTDOWN_MS = 3000;

    private static boolean nativeLibraryLoaded = false;

    private SteamUGC steamUGC;
    private SteamCallbackRunner steamCallbacksThread;
    private volatile long lastSteamInteractionTimestampMs;
    // Single pending operations map keyed by original long steamModId
    private final Map<Long, CompletableFuture<ItemResult>> pendingDownloadOperations = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<ItemResult>> pendingSubscribeOperations = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<ItemResult>> pendingUnsubscribeOperations = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<List<SteamModQueryResult>>> pendingSteamModQueryOperations = new ConcurrentHashMap<>();
    private final Object lock = this;

    static {
        try {
            nativeLibraryLoaded = SteamAPI.loadLibraries(new SteamLibraryLoader() {
                @Override
                public boolean loadLibrary(String libraryName) {
                    // This looks for [libraryName].dll in your working directory / java.library.path
                    if (!libraryName.endsWith("64")) {
                        libraryName += "64"; // we support only 64-bit platform, so we can force it to load 64-bit library
                    }
                    try {
                        System.loadLibrary(libraryName);
                    } catch (UnsatisfiedLinkError e2) {
                        log.warn("Failed to load library: {}", libraryName, e2);
                        return false;
                    }
                    return true;
                }
            });
            log.info("Native library loaded: {}", nativeLibraryLoaded);
            ensureSteamAppIdFile();
        } catch (Exception e) {
            log.error("Failed to load Steam native library", e);
        }
    }

    static void ensureSteamAppIdFile() {
        Path path = Paths.get("steam_appid.txt");

        try {
            boolean shouldWrite = true;

            // 1. Check if file exists and read current content
            if (Files.exists(path)) {
                String currentContent = Files.readString(path, StandardCharsets.UTF_8).trim();
                if (currentContent.equals(String.valueOf(XCOM2_APP_ID))) {
                    shouldWrite = false;
                    log.info("steam_appid.txt is already exist and correct");
                }
            }

            // 2. Write only if necessary
            if (shouldWrite) {
                Files.writeString(path, String.valueOf(XCOM2_APP_ID), StandardCharsets.UTF_8);
                log.info("steam_appid.txt updated to: " + XCOM2_APP_ID);
            }

        } catch (Exception e) {
            log.error("Error accessing steam_appid.txt: " + e.getMessage());
        }
    }

    public synchronized void maybeInitSteamApi() {
        lastSteamInteractionTimestampMs = System.currentTimeMillis();
        if (steamCallbacksThread != null) {
            return;
        }
        long beforeInitStartMs = System.currentTimeMillis();
        try {
            if (!SteamAPI.init()) {
                throw new IllegalStateException("SteamAPI init failed. Is Steam running?");
            }
            steamUGC = new SteamUGC(new SteamUGCCallback() {
                void handleCallback(String operation, Map<Long, CompletableFuture<ItemResult>> pendingOperations,
                                    SteamPublishedFileID publishedFileID, SteamResult result) {
                    Long originalId = getHandle(publishedFileID);

                    CompletableFuture<ItemResult> f = pendingOperations.remove(originalId);
                    if (f == null) {
                        log.debug("Received {} callback for known id {} but no pending future found. result: {}", operation, originalId, result);
                        return;
                    }
                    if (result != SteamResult.OK) {
                        f.completeExceptionally(new IllegalStateException(String.format(
                                "Operation %s failed for id %s. result: %s", operation, originalId, result)));
                        return;
                    } else {
                        f.complete(new ItemResult(originalId, result));
                    }
                }

                @Override
                public void onDownloadItemResult(int appID, SteamPublishedFileID publishedFileID, SteamResult result) {
                    handleCallback("onDownloadItemResult", pendingDownloadOperations, publishedFileID, result);
                }

                @Override
                public void onSubscribeItem(SteamPublishedFileID publishedFileID, SteamResult result) {
                    handleCallback("onSubscribeItem", pendingSubscribeOperations, publishedFileID, result);
                }

                @Override
                public void onUnsubscribeItem(SteamPublishedFileID publishedFileID, SteamResult result) {
                    handleCallback("onUnsubscribeItem", pendingUnsubscribeOperations, publishedFileID, result);
                }

                @Override
                public void onUGCQueryCompleted(SteamUGCQuery query, int numResultsReturned, int totalMatchingResults, boolean isCachedData, SteamResult result) {
                    // 1. Verify the network status
                    synchronized (lock) {
                        long handle = getHandle(query);
                        try {
                            var future = pendingSteamModQueryOperations.remove(handle);
                            if (future == null) {
                                log.error("Received UGC query callback for unknown query handle: {}", handle);
                                return;
                            }

                            if (result != SteamResult.OK) {
                                future.completeExceptionally(new IllegalStateException("Failed to fetch workshop details. Error status: " + result.name()));
                                return;
                            }

                            log.info("Received UGC query callback for handle: {}. Result: {}. Num results returned: {}. Total matching on Steam: {}. Cached data: {}",
                                    handle, result.name(), numResultsReturned, totalMatchingResults, isCachedData);

                            // Create the list to hold our cleanly parsed Java objects
                            List<SteamModQueryResult> parsedMods = new ArrayList<>(numResultsReturned);

                            // 2. Loop strictly through the number of results returned in this batch
                            for (int i = 0; i < numResultsReturned; i++) {
                                SteamUGCDetails details = new SteamUGCDetails();

                                if (steamUGC.getQueryUGCResult(query, i, details)) {
                                    // Extract core details
                                    // (Note: If your wrapper returns a SteamPublishedFileID object here, just call .get() or cast it appropriately)
                                    long steamModId = getHandle(details.getPublishedFileID());
                                    String title = details.getTitle();
                                    String description = details.getDescription();

                                    // Prepare the HashSet for children
                                    HashSet<Long> childIdsSet = new HashSet<>(details.getNumChildren());

                                    // Look up requirements array
                                    int numChildren = details.getNumChildren();
                                    if (numChildren > 0) {
                                        long[] childIdsArray = new long[numChildren];
                                        boolean success = steamUGC.getQueryUGCChildren(query, i, childIdsArray, numChildren);

                                        if (success) {
                                            // Populate the HashSet
                                            for (long childId : childIdsArray) {
                                                childIdsSet.add(childId);
                                            }
                                        } else {
                                            future.completeExceptionally(new IllegalStateException("Failed to retrieve child UGC identities for mod: " + title));
                                            return;
                                        }
                                    }

                                    // Construct the Lombok @Data object and add it to our list
                                    SteamModQueryResult modResult = new SteamModQueryResult(
                                            steamModId,
                                            title,
                                            description,
                                            Instant.ofEpochSecond(details.getTimeUpdated()),
                                            childIdsSet
                                    );

                                    parsedMods.add(modResult);
                                }
                            }
                            future.complete(parsedMods);
                        } finally {
                            steamUGC.releaseQueryUserUGCRequest(query);
                            lastSteamInteractionTimestampMs = System.currentTimeMillis();
                        }
                    }

                }
            });

            if (steamCallbacksThread == null) {
                steamCallbacksThread = new SteamCallbackRunner();
                Thread.startVirtualThread(steamCallbacksThread);
            }
            lastSteamInteractionTimestampMs = System.currentTimeMillis();
            log.info("Steam API initialized in {}ms", System.currentTimeMillis() - beforeInitStartMs);
        } catch (Throwable e) {
            throw new RuntimeException(String.format("Failed to initialize Steam API in %sms. Is Steam running?",
                    System.currentTimeMillis() - beforeInitStartMs), e);
        }
    }

    private synchronized void shutdownSteamApi() {
        if (steamCallbacksThread == null) {
            return;
        }
        steamCallbacksThread.stop();
        steamCallbacksThread = null;
        steamUGC = null;
        SteamAPI.shutdown();
        SteamAPI.releaseCurrentThreadMemory();
        log.info("Steam API shutdown");
    }

    public synchronized Collection<SteamUGC.ItemState> getItemState(long steamModId) {
        maybeInitSteamApi();
        Collection<SteamUGC.ItemState> result = steamUGC.getItemState(steamItemId(steamModId));
        result.remove(SteamUGC.ItemState.None);
        return result;
    }

    private synchronized CompletableFuture<ItemResult> asyncCall(
            Map<Long, CompletableFuture<ItemResult>> pendingOperations, long steamModId, Runnable action) {

        CompletableFuture<ItemResult> f = new CompletableFuture<>();
        pendingOperations.put(steamModId, f);
        try {
            maybeInitSteamApi();
            action.run();
        } catch (Throwable ex) {
            pendingOperations.remove(steamModId);
            f.completeExceptionally(ex);
        }
        return f;
    }

    public synchronized CompletableFuture<List<SteamModQueryResult>> getSteamModsInfo(Collection<Long> steamModIds, boolean returnChildren, boolean returnLongDescription) {
        if (steamModIds.size() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Too many mod ids requested. Max is " + MAX_PAGE_SIZE);
        }

        CompletableFuture<List<SteamModQueryResult>> f = new CompletableFuture<>();
        Long handle = null;
        try {
            maybeInitSteamApi();
            List<SteamPublishedFileID> idArray = steamModIds.stream().map(SteamPublishedFileID::new).toList();
            SteamUGCQuery queryHandle = steamUGC.createQueryUGCDetailsRequest(idArray);
            handle = getHandle(queryHandle);
            pendingSteamModQueryOperations.put(getHandle(queryHandle), f);
            steamUGC.setReturnLongDescription(queryHandle, returnLongDescription);
            steamUGC.setReturnChildren(queryHandle, returnChildren);
            steamUGC.sendQueryUGCRequest(queryHandle);
        } catch (Throwable ex) {
            pendingSteamModQueryOperations.remove(handle);
            f.completeExceptionally(ex);
        }
        return f;
    }

    public CompletableFuture<ItemResult> subscribeSteamMod(long steamModId) {
        return asyncCall(pendingSubscribeOperations, steamModId, () -> steamUGC.subscribeItem(steamItemId(steamModId)));
    }

    public CompletableFuture<ItemResult> unsubscribeSteamMod(long steamModId) {
        return asyncCall(pendingUnsubscribeOperations, steamModId, () -> steamUGC.unsubscribeItem(steamItemId(steamModId)));
    }

    public CompletableFuture<ItemResult> downloadSteamMod(long steamModId, boolean highPriority) {
        return asyncCall(pendingDownloadOperations, steamModId, () -> steamUGC.downloadItem(steamItemId(steamModId), highPriority));
    }

    /**
     * Deletes mod locally from steam mods directory.
     */
    public void deleteSteamMod(long steamModId) {
        throw new RuntimeException("Not implemented yet");
    }

    SteamPublishedFileID steamItemId(long steamModId) {
        return new SteamPublishedFileID(steamModId);
    }

    public boolean isWorking() {
        try {
            maybeInitSteamApi();
            return true;
        } catch (Exception e) {
            log.error("Steam is not working. Error {}", e.toString());
            return false;
        }
    }

    @Data
    public static class ItemResult {
        private final long steamModId;
        private final SteamResult result;
    }

    public class SteamCallbackRunner implements Runnable {
        private volatile boolean running = true;

        public void stop() {
            running = false;
        }

        @Override
        public void run() {
            while (running) {
                try {
                    Thread.sleep(100);
                    SteamAPI.runCallbacks();
                    synchronized (lock) {
                        long timestamp = System.currentTimeMillis();
                        if (timestamp - MS_DELAY_BEFORE_STEAM_API_SHUTDOWN_MS > lastSteamInteractionTimestampMs
                                && pendingDownloadOperations.isEmpty()
                                && pendingSubscribeOperations.isEmpty()
                                && pendingSteamModQueryOperations.isEmpty()
                                && pendingUnsubscribeOperations.isEmpty()) {
                            shutdownSteamApi();
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to run Steam API callback", e);
                }
            }
        }
    }

    @Data
    public static class SteamModQueryResult {
        private final long steamModId;
        private final String title;
        private final String description;
        private final Instant updatedAt;
        private final HashSet<Long> childIds;
    }

    public static Long getHandle(SteamNativeHandle fileID) {
        if (fileID == null) {
            return null;
        }
        try {
            Field f = SteamNativeHandle.class.getDeclaredField("handle");
            f.setAccessible(true);
            Object val = f.get(fileID);
            return (Long) val;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get handle field from SteamPublishedFileID " + fileID, e);
        }
    }

}
