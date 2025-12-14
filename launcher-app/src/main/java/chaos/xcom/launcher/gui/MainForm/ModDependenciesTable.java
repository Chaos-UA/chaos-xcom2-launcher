package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.TableColumn;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.gui.component.event.XMouseAdapter;
import chaos.xcom.launcher.gui.component.event.XTableModel;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.DeclarationSource;
import chaos.xcom.launcher.mod.dto.DependencyType;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.mod.dto.ModDependency;
import chaos.xcom.launcher.steam.SteamService;
import chaos.xcom.launcher.util.ColorConstant;
import jakarta.enterprise.inject.spi.CDI;
import org.apache.commons.lang3.StringUtils;
import org.jdesktop.swingx.decorator.AbstractHighlighter;
import org.jdesktop.swingx.decorator.ComponentAdapter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

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
                ModDependency row = model.getRow(convertRowIndexToModel(adapter.row));
                if (row == null || mod == null) {
                    return component;
                }
                int colIndex = convertColumnIndexToModel(adapter.column);
                if (colIndex == MOD_ID_COLUMN_INDEX) {
                    JCheckBox checkBox = (JCheckBox) component;
                    String mod = row.getMod();
                    checkBox.setHorizontalAlignment(SwingConstants.LEFT);
                    checkBox.setSelected(getModService().isModActive(mod));
                    checkBox.setText(mod);
                    checkBox.setToolTipText(mod);
                    if (!getModService().isModExist(mod)) {
                        checkBox.setEnabled(false);
                        component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                    }
                } else if (colIndex == TARGET_MOD_ID_COLUMN_INDEX) {
                    JCheckBox checkBox = (JCheckBox) component;
                    String mod = row.getTargetMod();
                    checkBox.setHorizontalAlignment(SwingConstants.LEFT);
                    checkBox.setSelected(getModService().isModActive(mod));
                    checkBox.setText(mod);
                    checkBox.setToolTipText(mod);
                    if (!getModService().isModExist(mod)) {
                        checkBox.setEnabled(false);
                        component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                    }
                }

                if (!row.isActive()) {
                    component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                } else {
                    if (row.isHasError()) {
                        component.setBackground(ColorConstant.ERROR);
                    }
                }
                if (row.getOverriddenByMod() != null) {
                    component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                }


                return component;
            }
        });

        model.apply(this);

        addMouseListener(new XMouseAdapter() {

            @Override
            public void popUpTrigger(MouseEvent e) {
                showMenu(e);
            }

            private void showMenu(MouseEvent e) {
                if (mod == null) {
                    return;
                }
                JPopupMenu menu = new JPopupMenu();
                JMenuItem editUserModRules = new JMenuItem("Edit mod rules");
                editUserModRules.addActionListener(ae -> {
                    getModService().openUserModRulesEditorDialog(mod);
                });
                menu.add(editUserModRules);

                int selectedRow = rowAtPoint(e.getPoint());

                if (selectedRow >= 0) {
                    menu.addSeparator();
                    setRowSelectionInterval(selectedRow, selectedRow);
                    ModDependency row = model.getRow(selectedRow);
                    Collection<Mod> mods = getModService().findModsByIds(Arrays.asList(row.getMod(), row.getTargetMod(), row.getOverriddenByMod(), row.getDeclaredInMod()));

                    for (Mod mod : mods) {
                        JMenuItem selectModItem = new JMenuItem("Select " + mod.getId());
                        selectModItem.addActionListener(ae -> {
                            ModService modService = getModService();
                            modService.selectMod(mod);
                        });
                        menu.add(selectModItem);
                    }
                }
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
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

    public class ModDependenciesModel extends XTableModel<ModDependency> {


        public ModDependenciesModel() {
            super(createTableColumns());
        }

        private static TableColumn[] createTableColumns() {
            return new TableColumn[]{
                    new TableColumn<>("Mod ID", String.class, ModDependency::getMod),
                    new TableColumn<>("Dependency", DependencyType.class, ModDependency::getDependencyType),
                    new TableColumn<>("Target Mod ID", String.class, ModDependency::getTargetMod),
                    new TableColumn<>("Declared in Mod ID", String.class, ModDependency::getDeclaredInMod),
                    new TableColumn<>("Overridden by Mod ID", String.class, ModDependency::getOverriddenByMod),
                    new TableColumn<>("Source", String.class, new Function<ModDependency, String>() {
                        @Override
                        public String apply(ModDependency modDependency) {
                            return modDependency.getSources().stream().map(Enum::name).collect(Collectors.joining(", "));
                        }
                    })
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
            ModDependency row = model.getRow(rowIndex);
            if (column == MOD_ID_COLUMN_INDEX) { // Active column
                getModService().setModActive(row.getMod(), !getModService().isModActive(row.getMod()));
            } else if (column == TARGET_MOD_ID_COLUMN_INDEX) {
                getModService().setModActive(row.getTargetMod(), !getModService().isModActive(row.getTargetMod()));
            } else {
                super.setValueAt(aValue, rowIndex, column);
            }
            fireTableDataChanged();
        }

        public void setMod(Mod mod) {
            if (mod == null) {
                rows = new ArrayList<>();
            } else {
                rows = mod.getDependencies();
            }
        }

        public ModDependency getRow(int row) {
            if (row < 0 || row >= getRowCount()) {
                return null;
            }
            return getRows().get(row);
        }
    }
}
