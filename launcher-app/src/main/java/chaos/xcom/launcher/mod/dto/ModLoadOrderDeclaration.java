package chaos.xcom.launcher.mod.dto;

import lombok.Data;

@Data
public class ModLoadOrderDeclaration {
    private String mod;
    private ModLoadOrder modLoadOrder;
    private String targetMod;
    private String declaredInMod;
    private String overriddenByMod;
    private DeclarationSource source;
}