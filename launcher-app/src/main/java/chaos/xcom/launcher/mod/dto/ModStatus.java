package chaos.xcom.launcher.mod.dto;

public enum ModStatus {
    OK,
    INACTIVE,
    DELETED,
    REQUIRE_DEPENDENCY,
    INCOMPATIBLE_DEPENDENCY,
    DUPLICATE,
    MISSING_REQUIRED_STEAM_MOD,
    CYCLIC_DEPENDENCY,
    /**
     * TODO ?
     */
    STEAM_ID_DUPLICATE,
}
