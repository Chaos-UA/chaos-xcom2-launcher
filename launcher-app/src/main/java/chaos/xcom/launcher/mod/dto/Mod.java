package chaos.xcom.launcher.mod.dto;

import chaos.xcom.launcher.highlander.dto.HighlanderModsConfig;
import chaos.xcom.launcher.highlander.dto.HighlanderRunPriorityGroup;
import lombok.Data;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Data
public class Mod {
    public static final int MOD_ORDER_DISABLED = Integer.MAX_VALUE;

    private String id;
    private String title;
    private String description;
    private boolean requiresXPACK;
    private String publishedFileId;
    private boolean active;
    private boolean exist;
    private String author;
    private Instant updatedAt;
    private Instant createdAt;
    private String tags;
    private long size;
    private File directory;
    private Set<Mod.Status> statuses;
    //private ModLoadOrderGroup loadOrderGroup;
    //private Set<String> runAfterMods = new HashSet<>();
    //private Set<String> runBeforeMods = new HashSet<>();
    //private Set<String> requiredMods = new HashSet<>();
    /**
     * Order at which to load mods on game start.
     * To guaranty order, special symbolic links will be created with names alphabetically sorted, that game recognize.
     */
    private int loadOrder;
    private HighlanderModsConfig highlanderModsConfig;
    private List<Mod> cycleMods = new ArrayList<>();
    private List<ModDependency> dependencies = new ArrayList<>();
    private List<ModLoadOrderGroupDeclaration> loadOrderGroups = new ArrayList<>();
    private List<ModLoadOrderDeclaration> loadOrders = new ArrayList<>();

    public void addLoadOrderGroupDeclaration(ModLoadOrderGroupDeclaration loadOrderGroupDeclaration) {
        for (ModLoadOrderGroupDeclaration prevLoadOrder : loadOrderGroups) {
            prevLoadOrder.setOverriddenByMod(loadOrderGroupDeclaration.getDeclaredInMod());
        }
        loadOrderGroups.add(loadOrderGroupDeclaration);
    }

    public void addLoadOrderDeclaration(ModLoadOrderDeclaration newLoadOrder) {
        for (ModLoadOrderDeclaration prevLoadOrder : loadOrders) {
            if (prevLoadOrder.getTargetMod().equals(newLoadOrder.getTargetMod())) {
                prevLoadOrder.setOverriddenByMod(newLoadOrder.getDeclaredInMod());
            }
        }
        loadOrders.add(newLoadOrder);
    }

    public void addDependency(ModDependency dependency) {
        dependencies.add(dependency);
    }

    public void clearStateForLoadOrderCalculation() {
        loadOrder = MOD_ORDER_DISABLED;
        cycleMods = new ArrayList<>();
        dependencies = new ArrayList<>();
        loadOrders = new ArrayList<>();
        loadOrderGroups = new ArrayList<>();
    }

    /**
     * TODO
     */
    private List<Mod> duplicateMods = new ArrayList<>();

    public File getModPreviewImgFile() {
        return new File(this.directory + "/ModPreview.jpg");
    }

    public void setActive(boolean active) {
        this.active = active;
        for (Mod duplicateMod : duplicateMods) {
            duplicateMod.setActive(active);
        }
    }

    public String getStatusAsString() {
        return this.statuses.stream().map(Enum::toString).collect(Collectors.joining(", "));
    }

    @Data
    public static class ModLoadOrderGroupDeclaration {
        private HighlanderRunPriorityGroup modLoadOrderGroup;
        private String targetMod;
        private String declaredInMod;
        private String overriddenByMod;
    }

    @Data
    public static class ModLoadOrderDeclaration {
        private String mod;
        private ModLoadOrder modLoadOrder;
        private String targetMod;
        private String declaredInMod;
        private String overriddenByMod;
    }

    @Data
    public static class ModDependency {
        private String mod;
        private DependencyType dependencyType;
        private String targetMod;
        private String declaredInMod;
        private String overriddenByMod;
    }

    public static enum DependencyType {
        REQUIRED,
        IGNORE_REQUIRED,
        INCOMPATIBLE
    }

    public static enum Status {
        OK,
        INACTIVE,
        DELETED,
        REQUIRE_DEPENDENCY,
        INCOMPATIBLE_DEPENDENCY
    }

}
