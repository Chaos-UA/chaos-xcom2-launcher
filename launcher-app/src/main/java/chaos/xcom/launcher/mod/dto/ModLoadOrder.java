package chaos.xcom.launcher.mod.dto;

public enum ModLoadOrder {
    LOAD_BEFORE,
    LOAD_AFTER_REQUIRED,
    LOAD_AFTER;

    public boolean isLoadBefore() {
        return this == LOAD_BEFORE;
    }

    public boolean isLoadAfter() {
        return this == LOAD_AFTER || this == LOAD_AFTER_REQUIRED;
    }
}
