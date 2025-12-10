package chaos.xcom.launcher.mod.dto;

import chaos.xcom.launcher.steam.SteamMod;
import lombok.Data;

@Data
public class ModDependency {
    private String mod;
    private DependencyType dependencyType;
    private String targetMod;
    private String declaredInMod;
    private String overriddenByMod;
    private DeclarationSource source;
    private boolean hasError;
}
