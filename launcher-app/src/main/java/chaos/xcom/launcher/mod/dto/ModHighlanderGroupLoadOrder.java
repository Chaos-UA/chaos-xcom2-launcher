package chaos.xcom.launcher.mod.dto;

import chaos.xcom.launcher.highlander.dto.HighlanderRunPriorityGroup;
import lombok.Data;

@Data
public class ModHighlanderGroupLoadOrder {
    private String mod;
    private HighlanderRunPriorityGroup priorityGroup;
    private String declaredInMod;
    private String overriddenByMod;
    private boolean isActive;
}
