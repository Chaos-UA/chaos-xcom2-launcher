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
    NEW,
    /**
     * TODO ?
     */
    STEAM_ID_DUPLICATE,
    /**
     * Unknown load order error
     */
    LOAD_ORDER_ERROR
}
