package chaos.xcom.launcher.mod.dto;

import lombok.Data;

import java.util.TreeSet;

@Data
public class ModDependency {
    private String mod;
    private DependencyType dependencyType;
    private String targetMod;
    private String declaredInMod;
    private String overriddenByMod;
    private TreeSet<DeclarationSource> sources = new TreeSet<>();
    private boolean isActive;
    private boolean isIgnored;
    private boolean hasError;
}
