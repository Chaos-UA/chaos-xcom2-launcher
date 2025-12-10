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
    /**
     * Mod directory size in bytes.
     */
    private Long size;
    private File directory;
    private Set<ModStatus> statuses = new LinkedHashSet<>();
    /**
     * Order at which to load mods on game start.
     * To guaranty order, special symbolic links will be created with names alphabetically sorted, that game recognize.
     */
    private int loadOrder;
    private HighlanderModsConfig highlanderModsConfig = new HighlanderModsConfig();

    /**
     * TODO
     */
    private List<UserRuleDeclaration> userRuleDeclarations = new ArrayList<>();

    /**
     * TODO
     */
    private List<ModDeclaredDependency> declaredDependencies = new ArrayList<>();
    /**
     * TODO
     */
    private List<ModLoadOrderDeclaration> declaredLoadOrders = new ArrayList<>();

    private List<List<Mod>> cycleMods = new ArrayList<>();
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

}
