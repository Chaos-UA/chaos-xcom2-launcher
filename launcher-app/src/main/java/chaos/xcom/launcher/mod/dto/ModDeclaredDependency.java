package chaos.xcom.launcher.mod.dto;

import chaos.xcom.launcher.steam.SteamMod;
import chaos.xcom.launcher.steam.SteamMod.SteamRequiredMod;
import lombok.Data;

@Data
public class ModDeclaredDependency {
    private String mod;
    private DependencyType dependencyType;
    private String targetMod;
    private String declaredInMod;
    private SteamRequiredMod steamRequiredMod;
    private DeclarationSource source;
}
