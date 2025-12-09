package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.TableColumn;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.gui.component.event.XTableModel;
import chaos.xcom.launcher.highlander.dto.HighlanderModConfig;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.DeclarationSource;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.mod.dto.Mod.DependencyType;
import chaos.xcom.launcher.steam.SteamMod;
import chaos.xcom.launcher.steam.SteamMod.SteamRequiredMod;
import jakarta.enterprise.inject.spi.CDI;
import lombok.Data;

import java.util.ArrayList;

public class ModDeclaredDependenciesTable extends XTable {
    private  ModDependenciesTableModel model;

    public ModDeclaredDependenciesTable() {
        this.model = new ModDependenciesTableModel();
        setSortable(false);
        model.apply(this);
        setMod(null);
    }

    public void setMod(Mod mod) {
        model.setMod(mod);
        model.apply(this);
        packAll();
    }

    @Data
    public static class ModDependencyTableRow {
        private String modId;
        private DependencyType dependencyType;
        private String targetModId;
        private DeclarationSource source;
    }

    ModService getModService() {
        return CDI.current().select(ModService.class).get();
    }

    public class ModDependenciesTableModel extends XTableModel<ModDependencyTableRow> {

        public ModDependenciesTableModel() {
            super(createTableColumns());
        }

        private static TableColumn[] createTableColumns() {
            return new TableColumn[]{
                    new TableColumn<>("Mod ID", String.class, ModDependencyTableRow::getModId),
                    new TableColumn<>("Dependency", DependencyType.class, ModDependencyTableRow::getDependencyType),
                    new TableColumn<>("Target Mod ID", String.class, ModDependencyTableRow::getTargetModId),
                    new TableColumn<>("Source", DeclarationSource.class, ModDependencyTableRow::getSource)
            };
        }

        public void setMod(Mod mod) {
            if (mod == null) {
                rows = new ArrayList<>();
            } else {
                rows = new ArrayList<>();

                SteamMod steamMod = mod.getSteamMod();
                for (SteamRequiredMod steamRequiredMod : steamMod.getRequiredSteamMods()) {
                    Mod targetSteamMod = getModService().findModBySteamModId(steamRequiredMod.getSteamModId()).orElse(null);
                    ModDependencyTableRow row = new ModDependencyTableRow();
                    row.setModId(mod.getId());
                    row.setDependencyType(DependencyType.REQUIRED);
                    if (targetSteamMod == null) { // todo open link and red?
                        row.setTargetModId("missing: " + steamRequiredMod.getSteamModName() + " - " + steamRequiredMod.getSteamModId());
                    } else {
                        row.setTargetModId(targetSteamMod.getId());
                    }
                    row.setSource(DeclarationSource.STEAM);
                    rows.add(row);
                }

                for (HighlanderModConfig modConfig : mod.getHighlanderModsConfig().getModConfigs().values()) {
                    for (String requiredModId : modConfig.getRequiredMods()) {
                        ModDependencyTableRow row = new ModDependencyTableRow();
                        row.setModId(modConfig.getMod());
                        row.setDependencyType(DependencyType.REQUIRED);
                        row.setTargetModId(requiredModId);
                        row.setSource(DeclarationSource.HIGHLANDER);
                        rows.add(row);
                    }

                    for (String ignoreRequiredModId : modConfig.getIgnoreRequiredMods()) {
                        ModDependencyTableRow row = new ModDependencyTableRow();
                        row.setModId(modConfig.getMod());
                        row.setDependencyType(DependencyType.REPLACED);
                        row.setTargetModId(ignoreRequiredModId);
                        row.setSource(DeclarationSource.HIGHLANDER);
                        rows.add(row);
                    }

                    for (String incompatibleId : modConfig.getIncompatibleMods()) {
                        ModDependencyTableRow row = new ModDependencyTableRow();
                        row.setModId(modConfig.getMod());
                        row.setDependencyType(DependencyType.INCOMPATIBLE);
                        row.setTargetModId(incompatibleId);
                        row.setSource(DeclarationSource.HIGHLANDER);
                        rows.add(row);
                    }
                }

            }
        }
    }
}
