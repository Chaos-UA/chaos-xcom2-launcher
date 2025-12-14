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
        LOAD_BEFORE,
        LOAD_AFTER;

        public ModLoadOrder toLoadOrder() {
            if (LOAD_BEFORE.equals(this)) {
                return  ModLoadOrder.LOAD_BEFORE;
            } else if (LOAD_AFTER.equals(this)) {
                return  ModLoadOrder.LOAD_AFTER;
            }
            return null;
        }
    }
}
