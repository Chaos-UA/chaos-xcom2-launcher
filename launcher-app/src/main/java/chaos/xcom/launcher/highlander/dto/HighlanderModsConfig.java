package chaos.xcom.launcher.highlander.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class HighlanderModsConfig {
    private String mod;
    private Map<String, HighlanderModConfig> modConfigs = new LinkedHashMap<>();

    public HighlanderModsConfig(String mod) {
        this.mod = mod;
    }

    public int getDependenciesCount() {
        return modConfigs.values().stream().mapToInt(HighlanderModConfig::getDependenciesCount).sum();
    }

    public int getRunOrderDependenciesCount() {
        return modConfigs.values().stream().mapToInt(HighlanderModConfig::getRunDependenciesCount).sum();
    }
}
