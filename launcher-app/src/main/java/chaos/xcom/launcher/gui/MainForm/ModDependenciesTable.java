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

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class ModDependenciesTable extends XTable {
    private static int MOD_ID_COLUMN_INDEX = 0;
    private static int TARGET_MOD_ID_COLUMN_INDEX = 2;

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
                if (row == null || mod == null) {
                    return component;
                }
                int colIndex = convertColumnIndexToModel(adapter.column);
                if (colIndex == MOD_ID_COLUMN_INDEX) {
                    JCheckBox checkBox = (JCheckBox) component;
                    checkBox.setHorizontalAlignment(SwingConstants.LEFT);
                    checkBox.setSelected(getModService().isModActive(row.getModId()));
                    checkBox.setText(row.getModId());
                    if (!getModService().isModExist(row.getModId())) {
                        checkBox.setEnabled(false);
                        //checkBox.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                        //JLabel lbl = new JLabel("  " + row.getTargetModId());
                        //component = lbl;
                        component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                    }
                } else if (colIndex == TARGET_MOD_ID_COLUMN_INDEX) {
                    JCheckBox checkBox = (JCheckBox) component;
                    checkBox.setHorizontalAlignment(SwingConstants.LEFT);
                    checkBox.setSelected(getModService().isModActive(row.getTargetModId()));
                    checkBox.setText(row.getTargetModId());
                    if (!getModService().isModExist(row.getTargetModId())) {
                        checkBox.setEnabled(false);
                        //JLabel lbl = new JLabel("  " + row.getTargetModId());
                        //component = lbl;
                        component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                    }
                }

                if (row.getOverriddenByModId() != null) {
                    component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                }
                if (row.isHasError()) {
                    component.setBackground(ColorConstant.ERROR.getColor());
                }

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
        private boolean hasError;
    }

    public class ModDependenciesModel extends XTableModel<ModDependenciesTableRow> {


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

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == MOD_ID_COLUMN_INDEX) {
                return Boolean.class;
            } else if (columnIndex == TARGET_MOD_ID_COLUMN_INDEX) {
                return Boolean.class;
            }
            return super.getColumnClass(columnIndex);
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int column) {
            ModDependenciesTableRow row = model.getRow(rowIndex);
            if (column == MOD_ID_COLUMN_INDEX) { // Active column
                getModService().setModActive(row.getModId(), !getModService().isModActive(row.getModId()));
            } else if (column == TARGET_MOD_ID_COLUMN_INDEX) {
                getModService().setModActive(row.getTargetModId(), !getModService().isModActive(row.getTargetModId()));
            } else {
                super.setValueAt(aValue, rowIndex, column);
            }
            fireTableDataChanged();
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
                    row.setHasError(modDependency.isHasError());
                    rows.add(row);
                }
            }
        }

        public ModDependenciesTableRow getRow(int row) {
            if (row < 0 || row >= getRowCount()) {
                return null;
            }
            return getRows().get(row);
        }
    }
}
