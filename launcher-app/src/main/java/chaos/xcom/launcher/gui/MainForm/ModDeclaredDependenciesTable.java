package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.TableColumn;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.gui.component.event.XTableModel;
import chaos.xcom.launcher.highlander.dto.HighlanderModConfig;
import chaos.xcom.launcher.mod.dto.Mod;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

public class ModDeclaredDependenciesTable extends XTable {
    private  ModDependenciesTableModel model;

    public ModDeclaredDependenciesTable() {
        this.model = new ModDependenciesTableModel();
        model.apply(this);
        setMod(null);
    }

    public void setMod(Mod mod) {
        model.setMod(mod);
        model.apply(this);
    }

    @Data
    public static class ModDependencyTableRow {
        private String modId;
        private String dependencyType;
        private String targetModId;
        private Boolean active;
    }

    public static class ModDependenciesTableModel extends XTableModel<ModDependencyTableRow> {

        public ModDependenciesTableModel() {
            super(createTableColumns());
        }

        private static TableColumn[] createTableColumns() {
            return new TableColumn[]{
                    new TableColumn<>("Mod ID", String.class, ModDependencyTableRow::getModId),
                    new TableColumn<>("Dependency", String.class, ModDependencyTableRow::getDependencyType),
                    new TableColumn<>("Target Mod ID", String.class, ModDependencyTableRow::getTargetModId),
                    new TableColumn<>("Active", Boolean.class, ModDependencyTableRow::getActive)
            };
        }

        public void setMod(Mod mod) {
            if (mod == null) {
                rows = new ArrayList<>();
            } else {
                rows = new ArrayList<>();
                for (HighlanderModConfig modConfig : mod.getHighlanderModsConfig().getModConfigs().values()) {
                    for (String requiredModId : modConfig.getRequiredMods()) {
                        ModDependencyTableRow row = new ModDependencyTableRow();
                        row.setModId(modConfig.getMod());
                        row.setDependencyType("Required");
                        row.setTargetModId(requiredModId);
                        rows.add(row);
                    }

                    for (String ignoreRequiredModId : modConfig.getIgnoreRequiredMods()) {
                        ModDependencyTableRow row = new ModDependencyTableRow();
                        row.setModId(modConfig.getMod());
                        row.setDependencyType("Ignore Required");
                        row.setTargetModId(ignoreRequiredModId);
                        rows.add(row);
                    }

                    for (String incompatibleId : modConfig.getIncompatibleMods()) {
                        ModDependencyTableRow row = new ModDependencyTableRow();
                        row.setModId(modConfig.getMod());
                        row.setDependencyType("Incompatible");
                        row.setTargetModId(incompatibleId);
                        rows.add(row);
                    }
                }

            }
        }
    }
}
