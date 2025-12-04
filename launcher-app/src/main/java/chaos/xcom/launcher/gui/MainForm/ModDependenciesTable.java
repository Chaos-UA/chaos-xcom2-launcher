package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.TableColumn;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.gui.component.event.XTableModel;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.mod.dto.Mod.ModDependency;
import chaos.xcom.launcher.util.ColorConstant;
import jakarta.enterprise.inject.spi.CDI;
import lombok.Data;
import org.jdesktop.swingx.decorator.AbstractHighlighter;
import org.jdesktop.swingx.decorator.ComponentAdapter;

import java.awt.*;
import java.util.ArrayList;

public class ModDependenciesTable extends XTable {
    private ModDependenciesModel model;
    private Mod mod;

    public ModDependenciesTable() {
        this.model = new ModDependenciesModel();
        setSortable(false);
        setMod(null);
        this.addHighlighter(new AbstractHighlighter() {
            @Override
            protected Component doHighlight(Component component, ComponentAdapter adapter) {
                ModDependenciesTableRow row = model.getRow(convertRowIndexToModel(adapter.row));
                if (mod == null || !mod.getId().equals(row.getModId())) {
                    return component;
                }

                ModService modService = getModService();
                if (row.getOverriddenByModId() != null) {
                    component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                } else if (row.getDependencyType() == Mod.DependencyType.REQUIRED
                        && !modService.isModActive(row.getTargetModId())) {
                    component.setBackground(ColorConstant.MISSING_DEPENDENCY_MOD.getColor());
                } else if (row.getDependencyType() == Mod.DependencyType.INCOMPATIBLE
                        && modService.isModActive(row.getTargetModId())) {
                    component.setBackground(ColorConstant.MISSING_DEPENDENCY_MOD.getColor());
                } // todo ignore required alternative

                return component;
            }
        });
        model.apply(this);
    }

    ModService getModService() {
        return CDI.current().select(ModService.class).get();
    }

    public void setMod(Mod mod) {
        this.mod = mod;
        model.setMod(mod);
        model.apply(this);
        packAll();
    }

    @Data
    public static class ModDependenciesTableRow {
        private String modId;
        private Mod.DependencyType dependencyType;
        private String targetModId;
        private String declaredInModId;
        private String overriddenByModId;
    }

    public static class ModDependenciesModel extends XTableModel<ModDependenciesTableRow> {

        public ModDependenciesModel() {
            super(createTableColumns());
        }

        private static TableColumn[] createTableColumns() {
            return new TableColumn[]{
                    new TableColumn<>("Mod ID", String.class, ModDependenciesTableRow::getModId),
                    new TableColumn<>("Dependency", Mod.DependencyType.class, ModDependenciesTableRow::getDependencyType),
                    new TableColumn<>("Target Mod ID", String.class, ModDependenciesTableRow::getTargetModId),
                    new TableColumn<>("Declared in Mod ID", String.class, ModDependenciesTableRow::getDeclaredInModId),
                    new TableColumn<>("Overridden by Mod ID", String.class, ModDependenciesTableRow::getOverriddenByModId)
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
                    row.setDependencyType(modDependency.getDependencyType());
                    row.setOverriddenByModId(modDependency.getOverriddenByMod());
                    rows.add(row);
                }
            }
        }

        public ModDependenciesTableRow getRow(int row) {
            return getRows().get(row);
        }
    }
}
