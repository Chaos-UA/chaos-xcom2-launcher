package chaos.xcom.launcher.mod.dto;

import lombok.Data;

@Data
public class UserRuleDeclaration {
    private String modId;
    private RuleType type;
    private String targetModId;

    public static enum RuleType {
        REQUIRED,
        IGNORE_REQUIRED,
        INCOMPATIBLE,
        LOAD_BEFORE,
        LOAD_AFTER;
    }
}
