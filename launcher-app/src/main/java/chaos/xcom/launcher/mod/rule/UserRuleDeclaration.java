package chaos.xcom.launcher.mod.rule;

import chaos.xcom.launcher.mod.dto.ModLoadOrder;
import lombok.Data;

@Data
public class UserRuleDeclaration {
    private Long id;
    private String modId;
    private RuleType type;
    private String targetModId;

    public UserRuleDeclaration() {}

    public UserRuleDeclaration(UserRuleDeclaration source) {
        this.id = source.id;
        this.modId = source.modId;
        this.type = source.type;
        this.targetModId = source.targetModId;
    }

    public static enum RuleType {
        REQUIRED,
        REPLACED,
        BEFORE,
        AFTER;

        public ModLoadOrder toLoadOrder() {
            if (BEFORE.equals(this)) {
                return  ModLoadOrder.BEFORE;
            } else if (AFTER.equals(this)) {
                return  ModLoadOrder.AFTER;
            }
            return null;
        }
    }
}
