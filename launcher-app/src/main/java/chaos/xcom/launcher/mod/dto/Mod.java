package chaos.xcom.launcher.mod.dto;

import chaos.xcom.launcher.highlander.dto.HighlanderModsConfig;
import chaos.xcom.launcher.highlander.dto.HighlanderRunPriorityGroup;
import chaos.xcom.launcher.mod.rule.UserRuleDeclaration;
import chaos.xcom.launcher.steam.SteamMod;
import com.sun.source.tree.Tree;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Getter
@Setter
public class Mod {
    public static final int MOD_ORDER_DISABLED = Integer.MAX_VALUE;

    private String id;
    private String title;
    private String xcomModFileContent;
    private boolean requiresXPACK;
    private String publishedFileId;
    /**
     * If not null then it will be used.
     */
    private String steamDbModId;
    /**
     * Parsed Steam ID from steam workshop directory.
     */
    private String steamModIdByDirName;
    private SteamMod steamMod = new SteamMod();
    private boolean active;
    private boolean isNewMod = true;

    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private Boolean exist;
    /**
     * Mod directory size in bytes.
     */
    private Long size;
    private File directory;
    private TreeSet<ModStatus> statuses = new TreeSet<>();
    /**
     * Order at which to load mods on game start.
     * To guaranty order, special symbolic links will be created with names alphabetically sorted, that game recognize.
     */
    private int loadOrder;
    private HighlanderModsConfig highlanderModsConfig = new HighlanderModsConfig();


    private List<ModDeclaredDependency> declaredDependencies = new ArrayList<>();
    /**
     * TODO
     */
    private List<ModLoadOrderDeclaration> declaredLoadOrders = new ArrayList<>();

    private List<List<Mod>> cycleMods = new ArrayList<>();
    private List<ModDependency> dependencies = new ArrayList<>();
    private List<ModHighlanderGroupLoadOrder> highlanderGroupLoadOrders = new ArrayList<>();
    private List<ModLoadOrderGroupDeclaration> loadOrderGroups = new ArrayList<>();
    private List<ModLoadOrderDeclaration> loadOrders = new ArrayList<>();

    public void addDeclaredDependency(ModDeclaredDependency dependency) {
        if (dependency.getMod().equals(getId())) {
            for (int i = declaredDependencies.size() - 1; i >= 0; i--) {
                ModDeclaredDependency existingDependency = declaredDependencies.get(i);
                if (Objects.equals(existingDependency.getMod(), dependency.getMod())
                        && Objects.equals(existingDependency.getDependencyType(), dependency.getDependencyType())
                        && Objects.equals(existingDependency.getTargetMod(), dependency.getTargetMod())
                        && dependency.getTargetMod() != null) {
                    dependency.getSources().addAll(existingDependency.getSources()); // all equals, merge sources
                    declaredDependencies.remove(i);
                }
            }
        }

        declaredDependencies.add(dependency);
    }

    public void addLoadOrderGroupDeclaration(ModLoadOrderGroupDeclaration loadOrderGroupDeclaration) {
        for (ModLoadOrderGroupDeclaration prevLoadOrder : loadOrderGroups) {
            prevLoadOrder.setOverriddenByMod(loadOrderGroupDeclaration.getDeclaredInMod());
        }
        loadOrderGroups.add(loadOrderGroupDeclaration);
    }

    public void addIncomingLoadOrderDeclaration(ModLoadOrderDeclaration loadOrderDeclarationFromAnotherMod) {
        if (loadOrderDeclarationFromAnotherMod.getModLoadOrder().isLoadAfterRequired()) {
            for (ModLoadOrderDeclaration prevLoadOrder : loadOrders) {
                if (loadOrderDeclarationFromAnotherMod.isActive() && prevLoadOrder.isActive
                        && prevLoadOrder.getTargetMod().equals(loadOrderDeclarationFromAnotherMod.getMod())) {
                    if (!prevLoadOrder.getModLoadOrder().isLoadAfterRequired()) {
                        // this one has higher priority
                        loadOrderDeclarationFromAnotherMod.setOverriddenByMod(prevLoadOrder.getDeclaredInMod());
                        loadOrderDeclarationFromAnotherMod.setActive(false);
                        log.info("Mod {} overridden {} with higher priority", prevLoadOrder, loadOrderDeclarationFromAnotherMod);
                        break;
                    }
                }
            }
        }

        loadOrders.add(loadOrderDeclarationFromAnotherMod);
    }

    public void addLoadOrder(ModLoadOrderDeclaration newLoadOrder) {
        if (newLoadOrder.getMod().equals(getId())) { // merge sources and remove duplicate
            for (int i = loadOrders.size() - 1; i >= 0; i--) {
                ModLoadOrderDeclaration existingOrder = loadOrders.get(i);
                if (Objects.equals(existingOrder.getMod(), newLoadOrder.getMod())
                        && Objects.equals(existingOrder.getModLoadOrder(), newLoadOrder.getModLoadOrder())
                        && Objects.equals(existingOrder.getTargetMod(), newLoadOrder.getTargetMod())
                        && Objects.equals(existingOrder.getDeclaredInMod(), newLoadOrder.getDeclaredInMod())
                        && Objects.equals(existingOrder.getOverriddenByMod(), newLoadOrder.getOverriddenByMod())) {
                    newLoadOrder.getSources().addAll(existingOrder.getSources()); // all equals, merge sources
                    loadOrders.remove(i);
                }
            }
        }

        for (ModLoadOrderDeclaration prevLoadOrder : loadOrders) {
            if (getId().equals(newLoadOrder.getMod()) && prevLoadOrder.getTargetMod().equals(newLoadOrder.getTargetMod())) {
                prevLoadOrder.setOverriddenByMod(newLoadOrder.getDeclaredInMod());
                prevLoadOrder.setActive(false);
                prevLoadOrder.setHasError(false);
            }
        }
        loadOrders.add(newLoadOrder);
    }

    public void addHighlanderGroupLoadOrder(ModHighlanderGroupLoadOrder groupLoadOrder) {
        for (ModHighlanderGroupLoadOrder prev : highlanderGroupLoadOrders) {
            if (prev.getOverriddenByMod() != null) {
                prev.setOverriddenByMod(groupLoadOrder.getDeclaredInMod());
                prev.setActive(false);
            }
        }
        highlanderGroupLoadOrders.add(groupLoadOrder);
    }

    public void addDependency(ModDependency dependency) {
        if (dependency.getMod().equals(getId())) {
            for (int i = dependencies.size() - 1; i >= 0; i--) {
                ModDependency existingDependency = dependencies.get(i);
                if (Objects.equals(existingDependency.getMod(), dependency.getMod())
                        && Objects.equals(existingDependency.getDependencyType(), dependency.getDependencyType())
                        && Objects.equals(existingDependency.getTargetMod(), dependency.getTargetMod())
                        && Objects.equals(existingDependency.getDeclaredInMod(), dependency.getDeclaredInMod())
                        && Objects.equals(existingDependency.getOverriddenByMod(), dependency.getOverriddenByMod())) {
                    dependency.getSources().addAll(existingDependency.getSources()); // all equals, merge sources
                    dependencies.remove(i);
                }
            }
        }

        dependencies.add(dependency);
    }

    public void clearStateForLoadOrderCalculation() {
        exist = null;
        loadOrder = MOD_ORDER_DISABLED;
        statuses = new TreeSet<>();
        highlanderGroupLoadOrders = new ArrayList<>();
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
        private TreeSet<DeclarationSource> sources = new TreeSet<>();
        private boolean isActive;
        private boolean hasError;

        public void setOverriddenByMod(String overriddenByMod) {
            this.overriddenByMod = overriddenByMod;
            this.setHasError(false);
            this.setActive(false);
        }
    }

}
