package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.TableColumn;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.gui.component.event.XTableModel;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.mod.dto.Mod.ModDependency;
import lombok.Data;

import java.util.ArrayList;

public class ModDependenciesTable extends XTable {
    private ModDependenciesModel model;

    public ModDependenciesTable() {
        this.model = new ModDependenciesModel();
        setMod(null);
        model.apply(this);
    }

    public void setMod(Mod mod) {
        model.setMod(mod);
        model.apply(this);
    }

    @Data
    public static class ModDependenciesTableRow {
        private String modId;
        private String dependencyType;
        private String targetModId;
        private String declaredInModId;
        private Boolean active;
    }

    public static class ModDependenciesModel extends XTableModel<ModDependenciesTableRow> {

        public ModDependenciesModel() {
            super(createTableColumns());
        }

        private static TableColumn[] createTableColumns() {
            return new TableColumn[]{
                    new TableColumn<>("Mod ID", String.class, ModDependenciesTableRow::getModId),
                    new TableColumn<>("Dependency", String.class, ModDependenciesTableRow::getDependencyType),
                    new TableColumn<>("Target Mod ID", String.class, ModDependenciesTableRow::getTargetModId),
                    new TableColumn<>("Declared in Mod ID", String.class, ModDependenciesTableRow::getDeclaredInModId),
                    new TableColumn<>("Active", Boolean.class, ModDependenciesTableRow::getActive)
            };
        }

        public void setMod(Mod mod) {
            if (mod == null) {
                rows = new ArrayList<>();
            } else {
                rows = new ArrayList<>();
                for (ModDependency modDependency : mod.getDependencies()) {
                    ModDependenciesTableRow row = new ModDependenciesTableRow();
                    row.setModId(modDependency.getMod());
                    row.setTargetModId(modDependency.getTargetMod());
                    row.setDeclaredInModId(modDependency.getDeclaredInMod());
                    row.setDependencyType(modDependency.getDependencyType().name());
                    row.setActive(false);
                    rows.add(row);
                }
            }
        }
    }
}
