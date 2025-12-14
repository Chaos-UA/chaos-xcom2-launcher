package chaos.xcom.launcher.mod.dto;

import chaos.xcom.launcher.steam.SteamMod.SteamRequiredMod;
import lombok.Data;

import java.util.TreeSet;

@Data
public class ModDeclaredDependency {
    private String mod;
    private DependencyType dependencyType;
    private String targetMod;
    private SteamRequiredMod steamRequiredMod;
    private TreeSet<DeclarationSource> sources = new TreeSet<>();
    private boolean hasError;
}
