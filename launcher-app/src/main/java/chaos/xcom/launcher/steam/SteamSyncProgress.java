package chaos.xcom.launcher.steam;

import lombok.Data;

@Data
public class SteamSyncProgress {
    public final int syncedModsCount;
    public final int totalModsToSyncCount;
    /**
     * Current steam mod ID which is in progress.
     */
    public final Long steamModId;


    public SteamSyncProgress(int syncedModsCount, int totalModsToSyncCount) {
        this(syncedModsCount, totalModsToSyncCount, null);
    }

    public SteamSyncProgress(int syncedModsCount, int totalModsToSyncCount, Long steamModId) {
        this.syncedModsCount = syncedModsCount;
        this.totalModsToSyncCount = totalModsToSyncCount;
        this.steamModId = steamModId;
    }

    public boolean isComplete() {
        return totalModsToSyncCount == 0 || syncedModsCount == totalModsToSyncCount;
    }
}
