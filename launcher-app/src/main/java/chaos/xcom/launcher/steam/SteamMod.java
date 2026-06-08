package chaos.xcom.launcher.steam;

import com.codedisaster.steamworks.SteamUGC;
import lombok.Data;
import lombok.ToString;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

@Data
public class SteamMod {
    private Long steamModId;
    private String steamModName;

    @ToString.Exclude
    private String description;
    private Instant syncedAt;
    private Instant lastUpdatedAt;
    private Instant lastDownloadedAt;
    private TreeSet<SteamUGC.ItemState> states = new TreeSet<>();
    private Set<SteamRequiredMod> requiredSteamMods = new LinkedHashSet<>();

    @Data
    public static class SteamRequiredMod {
        private Long steamModId;
        private String steamModName;
    }
}
