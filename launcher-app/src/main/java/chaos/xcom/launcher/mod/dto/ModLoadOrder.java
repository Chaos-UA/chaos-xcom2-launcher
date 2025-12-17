package chaos.xcom.launcher.mod.dto;

public enum ModLoadOrder {
    BEFORE,
    AFTER_REQUIRED,
    AFTER_REQUIRED_REPLACEMENT,
    AFTER;

    /**
     * this one has less priority than LOAD_BEFORE/LOAD_AFTER
     */
    public boolean isLoadAfterRequired() {
        return  this == AFTER_REQUIRED || this == AFTER_REQUIRED_REPLACEMENT;
    }

    public boolean isLoadBefore() {
        return this == BEFORE;
    }

    public boolean isLoadAfter() {
        return this == AFTER || isLoadAfterRequired();
    }
}
