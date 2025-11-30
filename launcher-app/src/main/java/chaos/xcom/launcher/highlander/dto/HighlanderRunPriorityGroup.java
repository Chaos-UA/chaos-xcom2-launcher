package chaos.xcom.launcher.highlander.dto;

/**
 * This is useful if you want to make a mod that will override changes done by another mod in one
 * of the X2 DLC Info methods.
 *
 * RunBefore - an array of DLC Identifiers of mods that will execute their methods after your mod.
 *
 * RunAfter and RunBefore work only within their Run Priority Group. For example,
 * if your mod uses RUN_STANDARD, you will not be able to change its DLC Run Order to be before
 * another mod in RUN_LAST group, or after RUN_LAST group.
 *
 * [DLCIdentifier_Of_Your_Mod CHDLCRunOrder]
 * +RunAfter=DLCIdentifier_A
 * +RunAfter=DLCIdentifier_B
 * +RunAfter=DLCIdentifier_C
 * +RunBefore=DLCIdentifier_D
 * RunPriorityGroup=RUN_STANDARD
 */
public enum HighlanderRunPriorityGroup {
    /**
     * Mods with this priority group execute their X2DLCInfo methods before mods from RUN_STANDARD.
     */
    RUN_FIRST,
    RUN_STANDARD,
    /**
     * Execute their methods after both of the other Priority Groups
     */
    RUN_LAST;

    public static HighlanderRunPriorityGroup parse(String value) {
        return valueOf(value.trim().toUpperCase());
    }
}
