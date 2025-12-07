package chaos.xcom.launcher.steam;

import lombok.Data;
import lombok.ToString;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class SteamMod {
    private String steamModId;
    private String steamModName;

    @ToString.Exclude
    private String description;
    private Instant updatedAt;
    private Set<SteamRequiredMod> requiredSteamMods = new LinkedHashSet<>();

    @Data
    public static class SteamRequiredMod {
        private String steamModId;
        private String steamModName;
    }
}
