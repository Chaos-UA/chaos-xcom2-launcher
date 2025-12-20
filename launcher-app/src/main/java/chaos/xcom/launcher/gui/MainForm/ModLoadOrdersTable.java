package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.TableColumn;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.gui.component.event.XMouseAdapter;
import chaos.xcom.launcher.gui.component.event.XTableModel;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.DeclarationSource;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.mod.dto.Mod.ModLoadOrderDeclaration;
import chaos.xcom.launcher.mod.dto.ModHighlanderGroupLoadOrder;
import chaos.xcom.launcher.util.ColorConstant;
import jakarta.enterprise.inject.spi.CDI;
import lombok.Data;
import org.jdesktop.swingx.decorator.AbstractHighlighter;
import org.jdesktop.swingx.decorator.ComponentAdapter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mod final load orders table.
 */
public class ModLoadOrdersTable extends XTable {
    private static final int MOD_ID_COLUMN_INDEX = 0;
    private static final int TARGET_MOD_ID_COLUMN_INDEX = 2;
    private ModDeclaredRunOrdersModel model;
    private Mod mod;

    public ModLoadOrdersTable() {
        this.model = new ModDeclaredRunOrdersModel();
        setSortable(false);
        setMod(null);
        this.addHighlighter(new AbstractHighlighter() {
            @Override
            protected Component doHighlight(Component component, ComponentAdapter adapter) {
                ModRunOrderTableRow row = model.getRow(convertRowIndexToModel(adapter.row));
                if (row == null) {
                    return component;
                }
                int colIndex = convertColumnIndexToModel(adapter.column);
                ModService modService = getModService();

                if (colIndex == MOD_ID_COLUMN_INDEX) {
                    if (row.getMod() == null) { // highlander run priority group
                        component = new JLabel(); // hide checkbox
                    } else {
                        JCheckBox checkBox = (JCheckBox) component;
                        String mod = row.getMod();
                        checkBox.setHorizontalAlignment(SwingConstants.LEFT);
                        checkBox.setSelected(getModService().isModActive(mod));
                        checkBox.setText(mod);
                        checkBox.setToolTipText(mod);
                        if (!modService.isModExist(mod)) {
                            checkBox.setEnabled(false);
                            component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                        }
                    }
                } else if (colIndex == TARGET_MOD_ID_COLUMN_INDEX) {
                    JCheckBox checkBox = (JCheckBox) component;
                    String mod = row.getTargetMod();
                    checkBox.setHorizontalAlignment(SwingConstants.LEFT);
                    checkBox.setSelected(getModService().isModActive(mod));
                    checkBox.setText(mod);
                    checkBox.setToolTipText(mod);
                    if (!modService.isModExist(mod)) {
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

                if (row.getOverriddenByModId() != null) {
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
                JMenuItem editUserModRules = new JMenuItem("Edit user mod rules");
                editUserModRules.addActionListener(ae -> {
                    getModService().openUserModRulesEditorDialog(mod);
                });
                menu.add(editUserModRules);

                int selectedRow = rowAtPoint(e.getPoint());

                if (selectedRow >= 0) {
                    menu.addSeparator();
                    setRowSelectionInterval(selectedRow, selectedRow);
                    ModRunOrderTableRow row = model.getRow(selectedRow);
                    Collection<Mod> mods = getModService().findModsByIds(Arrays.asList(row.getMod(), row.getTargetMod(), row.getOverriddenByModId(), row.getDeclaredInModId()));

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

    public void setMod(Mod mod) {
        this.mod = mod;
        model.setMod(mod);
        model.apply(this);
        packAll();
    }

    ModService getModService() {
        return CDI.current().select(ModService.class).get();
    }

    @Data
    public static class ModRunOrderTableRow {
        private String mod;
        private String runOrderType;
        private String targetMod;
        private String declaredInModId;
        private String overriddenByModId;
        private TreeSet<DeclarationSource> sources = new TreeSet<>();
        private boolean isActive;
        private boolean isHasError;
    }

    public class ModDeclaredRunOrdersModel extends XTableModel<ModRunOrderTableRow> {

        public ModDeclaredRunOrdersModel() {
            super(createTableColumns());
        }

        private static TableColumn[] createTableColumns() {
            return new TableColumn[]{
                    new TableColumn<>("Mod ID", String.class, ModRunOrderTableRow::getMod),
                    new TableColumn<>("Run Order", String.class, ModRunOrderTableRow::getRunOrderType),
                    new TableColumn<>("Target Mod ID", String.class, ModRunOrderTableRow::getTargetMod),
                    new TableColumn<>("Declared in Mod ID", String.class, ModRunOrderTableRow::getDeclaredInModId),
                    new TableColumn<>("Overridden by Mod ID", String.class, ModRunOrderTableRow::getOverriddenByModId),
                    new TableColumn<>("Source", String.class, new Function<ModRunOrderTableRow, String>() {
                        @Override
                        public String apply(ModRunOrderTableRow modRunOrderTableRow) {
                            return modRunOrderTableRow.getSources().stream().map(Enum::name).collect(Collectors.joining(", "));
                        }
                    })
            };
        }

        public void setMod(Mod mod) {
            if (mod == null) {
                rows = new ArrayList<>();
            } else {
                rows = new ArrayList<>();
                for (ModHighlanderGroupLoadOrder loadOrder : mod.getHighlanderGroupLoadOrders()) {
                    ModRunOrderTableRow row = new ModRunOrderTableRow();
                    row.setTargetMod(mod.getId());
                    row.setDeclaredInModId(loadOrder.getDeclaredInMod());
                    row.setOverriddenByModId(loadOrder.getOverriddenByMod());
                    row.setRunOrderType(loadOrder.getPriorityGroup().name());
                    row.getSources().add(DeclarationSource.HIGHLANDER);
                    row.setActive(loadOrder.isActive());
                    row.setHasError(false);
                    rows.add(row);
                }
                for (ModLoadOrderDeclaration loadOrder : mod.getLoadOrders()) {
                    ModRunOrderTableRow row = new ModRunOrderTableRow();
                    row.setMod(loadOrder.getMod());
                    row.setTargetMod(loadOrder.getTargetMod());
                    row.setDeclaredInModId(loadOrder.getDeclaredInMod());
                    row.setOverriddenByModId(loadOrder.getOverriddenByMod());
                    row.setRunOrderType(loadOrder.getModLoadOrder().name());
                    row.setSources(loadOrder.getSources());
                    row.setActive(loadOrder.isActive());
                    row.setHasError(loadOrder.isHasError());
                    rows.add(row);
                }
            }
        }

        public ModRunOrderTableRow getRow(int row) {
            if (row < 0 || row >= rows.size()) {
                return null;
            }
            return getRows().get(row);
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
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int column) {
            ModRunOrderTableRow row = getRow(rowIndex);
            if (column == MOD_ID_COLUMN_INDEX) { // Active column
                getModService().setModActive(row.getMod(), !getModService().isModActive(row.getMod()));
            } else if (column == TARGET_MOD_ID_COLUMN_INDEX) {
                getModService().setModActive(row.getTargetMod(), !getModService().isModActive(row.getTargetMod()));
            } else {
                super.setValueAt(aValue, rowIndex, column);
            }
            fireTableDataChanged();
        }
    }
}
