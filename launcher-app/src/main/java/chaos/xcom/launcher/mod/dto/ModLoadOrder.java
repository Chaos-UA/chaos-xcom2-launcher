package chaos.xcom.launcher.mod.dto;

public enum ModLoadOrder {
    LOAD_BEFORE,
    LOAD_AFTER_REQUIRED,
    LOAD_AFTER_REQUIRED_REPLACEMENT,
    LOAD_AFTER;

    /**
     * this one has less priority than LOAD_BEFORE/LOAD_AFTER
     */
    public boolean isLoadAfterRequired() {
        return  this == LOAD_AFTER_REQUIRED || this == LOAD_AFTER_REQUIRED_REPLACEMENT;
    }

    public boolean isLoadBefore() {
        return this == LOAD_BEFORE;
    }

    public boolean isLoadAfter() {
        return this == LOAD_AFTER || isLoadAfterRequired();
    }
}
