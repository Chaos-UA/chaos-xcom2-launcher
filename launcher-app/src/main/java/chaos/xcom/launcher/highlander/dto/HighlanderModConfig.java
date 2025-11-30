package chaos.xcom.launcher.highlander.dto;

import chaos.xcom.launcher.mod.dto.ModLoadOrder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
public class HighlanderModConfig {
    /**
     * Target mod.
     */
    private String mod;
    private HighlanderRunPriorityGroup runPriorityGroup = null;
    //private Set<String> runAfterMods = new HashSet<>();
    //private Set<String> runBeforeMods = new HashSet<>();
    private Set<String> requiredMods = new HashSet<>();
    private List<RunOrderDeclaration> runOrderDeclarations = new ArrayList<>();
    private Set<String> ignoreRequiredMods = new HashSet<>();
    private Set<String> incompatibleMods = new HashSet<>();

    public HighlanderModConfig(String mod) {
        this.mod = mod;
    }

    public int getDependenciesCount() {
        return requiredMods.size() + ignoreRequiredMods.size() + incompatibleMods.size();
    }

    public int getRunDependenciesCount() {
        int count = runOrderDeclarations.size();
        if (runPriorityGroup != null) {
            count++;
        }
        return count;
    }

    @Data
    public static class RunOrderDeclaration {
        private ModLoadOrder modLoadOrder;
        private String targetMod;
    }
}
