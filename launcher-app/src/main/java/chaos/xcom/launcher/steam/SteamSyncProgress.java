package chaos.xcom.launcher.steam;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class SteamSyncProgress {
    public final int syncedModsCount;
    public final int totalModsToSyncCount;

    public boolean isComplete() {
        return totalModsToSyncCount == 0;
    }
}
