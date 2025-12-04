package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.TableColumn;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.util.ComparatorUtils;
import chaos.xcom.launcher.util.FileUtils;
import jakarta.enterprise.inject.spi.CDI;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Slf4j
public class ModTableModel extends AbstractTableModel {
    @Getter
    private List<Mod> mods;
    private String modFilter = "";
    private final TableRowSorter<ModTableModel> rowSorter;

    private final TableColumn[] columns = new TableColumn[]{
            new TableColumn<>("Active", Boolean.class, Mod::isActive),
            new TableColumn<>("Title", String.class, Mod::getTitle),
            new TableColumn<>("ID", String.class, Mod::getId),
            new TableColumn<>("Status", String.class, Mod::getStatusAsString),
            new TableColumn<>("Order", Integer.class, Mod::getLoadOrder, new Function<Integer, String>() {
                @Override
                public String apply(Integer order) {
                    return Objects.equals(order, Mod.MOD_ORDER_DISABLED) ? "" : String.valueOf(order);
                }
            }),
            new TableColumn<>("Declared Dependencies", Integer.class, new Function<Mod, Integer>() {
                @Override
                public Integer apply(Mod mod) {
                    return mod.getHighlanderModsConfig().getDependenciesCount();
                }
            }),
            new TableColumn<>("Declared Orders", Integer.class, new Function<Mod, Integer>() {
                @Override
                public Integer apply(Mod mod) {
                    return mod.getHighlanderModsConfig().getRunOrderDependenciesCount();
                }
            }),
            new TableColumn<>("WOTC", Boolean.class, Mod::isRequiresXPACK, new Function<Boolean, String>() {
                @Override
                public String apply(Boolean forWotc) {
                    if (forWotc == null) {
                        return "?";
                    }
                    return forWotc ? "Yes" : "No";
                }
            }),
            new TableColumn<>("Steam ID", String.class, Mod::getPublishedFileId),
            new TableColumn<>("Size", Long.class, Mod::getSize, FileUtils::formatSizeAsMb)
    };

    public ModTableModel(List<Mod> mods) {
        this.mods = mods;
        this.rowSorter = new TableRowSorter<>(this);
    }

    public void setModFilter(String modFilterInput) {
        this.modFilter = StringUtils.stripToEmpty(modFilterInput).toLowerCase();
        if (modFilter.isEmpty()) {
            rowSorter.setRowFilter(null);
        } else {
            rowSorter.setRowFilter(new RowFilter<ModTableModel, Integer>() {
                @Override
                public boolean include(Entry<? extends ModTableModel, ? extends Integer> entry) {
                    if (StringUtils.isEmpty(modFilter)) {
                        return false;
                    }
                    Mod mod = getModByRawIndex(entry.getIdentifier());
                    return mod.getId().toLowerCase().contains(modFilter)
                            || mod.getTitle().toLowerCase().contains(modFilter);
                }
            });
        }
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return mods.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column].name;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Mod mod = mods.get(rowIndex);
        return columns[columnIndex].extractor.apply(mod);
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        Mod mod = mods.get(rowIndex);
        if (columnIndex == 0) { // Active column
            mod.setActive((boolean) aValue);
            fireTableCellUpdated(rowIndex, columnIndex);
            getModService().setModActive(mod.getId(), mod.isActive());
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return true; // Make cells non-editable
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columns[columnIndex].type;
    }

    public void apply(XTable tbl) {
        tbl.setModel(this);
        tbl.setRowSorter(rowSorter);

        for (int i = 0; i < columns.length; i++) {
            TableColumn column = columns[i];
            if (String.class.equals(column.type)) {
                rowSorter.setComparator(i, ComparatorUtils.STRING_NUMERIC_COMPARATOR);
            }

            Function<Object, String> renderAs = column.renderAs;
            if (renderAs != null) {
                tbl.getColumnModel().getColumn(i).setCellRenderer(new DefaultTableCellRenderer() {
                    @Override
                    protected void setValue(Object value) {
                        if (value == null) {
                            super.setValue(value);
                        } else {
                            setText(renderAs.apply(value));
                        }
                    }
                });
            }
        }
    }

    public Mod getModByRawIndex(int modIndex) {
        return mods.get(modIndex);
    }

    ModService getModService() {
        return CDI.current().select(ModService.class).get();
    }

    public void setMods(List<Mod> parsedMods) {
        this.mods = parsedMods;
        fireTableDataChanged();
    }
}