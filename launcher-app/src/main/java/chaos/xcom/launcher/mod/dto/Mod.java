package chaos.xcom.launcher.mod.dto;

import chaos.xcom.launcher.highlander.dto.HighlanderModConfig;
import chaos.xcom.launcher.highlander.dto.HighlanderModsConfig;
import chaos.xcom.launcher.highlander.dto.HighlanderRunPriorityGroup;
import chaos.xcom.launcher.steam.SteamMod;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
public class Mod {
    public static final int MOD_ORDER_DISABLED = Integer.MAX_VALUE;

    private String id;
    private String title;
    private String xcomModFileContent;
    private boolean requiresXPACK;
    private String publishedFileId;
    private SteamMod steamMod = new SteamMod();
    private boolean active;

    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private Boolean exist;
    private long size;
    private File directory;
    private Set<Mod.Status> statuses;
    /**
     * Order at which to load mods on game start.
     * To guaranty order, special symbolic links will be created with names alphabetically sorted, that game recognize.
     */
    private int loadOrder;
    private HighlanderModsConfig highlanderModsConfig = new HighlanderModsConfig();

    /**
     * TODO
     */
    private List<ModDependency> declaredDependencies = new ArrayList<>();
    /**
     * TODO
     */
    private List<ModLoadOrderDeclaration> declaredLoadOrders = new ArrayList<>();

    private List<List<Mod>> cycleMods = new ArrayList<>();
    private List<ModDependency> dependencies = new ArrayList<>();
    private List<ModLoadOrderGroupDeclaration> loadOrderGroups = new ArrayList<>();
    private List<ModLoadOrderDeclaration> loadOrders = new ArrayList<>();

    public List<ModDeclaredDependency> getDeclaredDependencies() {
        ArrayList<ModDeclaredDependency> result = new ArrayList<>();

        // todo user declarations

        for (String requiredSteamModId : steamMod.getRequiredSteamMods().stream().map(SteamMod.SteamRequiredMod::getSteamModId).toList()) {
            ModDeclaredDependency declaredDependency = new ModDeclaredDependency();
            declaredDependency.setMod(this.getId());
            declaredDependency.setTargetMod(requiredSteamModId);
            declaredDependency.setDependencyType(DependencyType.REQUIRED);
            declaredDependency.setDeclaredInMod(this.getId());
            declaredDependency.setSource(DeclarationSource.STEAM);
            result.add(declaredDependency);
        }

        HighlanderModConfig highlanderModConfig = highlanderModsConfig.getModConfigs().get(this.getId());
        for (String requiredModId : highlanderModConfig.getRequiredMods()) {
            ModDeclaredDependency declaredDependency = new ModDeclaredDependency();
            declaredDependency.setMod(this.getId());
            declaredDependency.setTargetMod(requiredModId);
            declaredDependency.setDependencyType(DependencyType.REQUIRED);
            declaredDependency.setDeclaredInMod(this.getId());
            declaredDependency.setSource(DeclarationSource.HIGHLANDER);
            result.add(declaredDependency);
        }
        for (String incompatibleModId : highlanderModConfig.getIncompatibleMods()) {
            ModDeclaredDependency declaredDependency = new ModDeclaredDependency();
            declaredDependency.setMod(this.getId());
            declaredDependency.setTargetMod(incompatibleModId);
            declaredDependency.setDependencyType(DependencyType.INCOMPATIBLE);
            declaredDependency.setDeclaredInMod(this.getId());
            declaredDependency.setSource(DeclarationSource.HIGHLANDER);
            result.add(declaredDependency);
        }
        for (String ignoredRequiredModId : highlanderModConfig.getIgnoreRequiredMods()) {
            ModDeclaredDependency declaredDependency = new ModDeclaredDependency();
            declaredDependency.setMod(this.getId());
            declaredDependency.setTargetMod(ignoredRequiredModId);
            declaredDependency.setDependencyType(DependencyType.IGNORE_REQUIRED);
            declaredDependency.setDeclaredInMod(this.getId());
            declaredDependency.setSource(DeclarationSource.HIGHLANDER);
            result.add(declaredDependency);
        }

        return result;
    }

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
        exist = null;
        loadOrder = MOD_ORDER_DISABLED;
        cycleMods = new ArrayList<>();
        dependencies = new ArrayList<>();
        loadOrders = new ArrayList<>();
        loadOrderGroups = new ArrayList<>();
    }

    public boolean isExist() {
        if (exist == null) {
            exist = directory != null && directory.exists() && directory.isDirectory();
        }
        return exist;
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
        private boolean hasError;
    }

    @Data
    public static class ModDeclaredDependency {
        private String mod;
        private DependencyType dependencyType;
        private String targetMod;
        private String declaredInMod;
        private DeclarationSource source;
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
        INCOMPATIBLE_DEPENDENCY,
        CYCLIC_DEPENDENCY
    }

}
