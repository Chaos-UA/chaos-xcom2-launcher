package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.TableColumn;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.util.ComparatorUtils;
import chaos.xcom.launcher.util.FileUtils;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

public class ModTableModel extends AbstractTableModel {
    private final List<Mod> mods;

    private final TableColumn[] columns = new TableColumn[]{
            new TableColumn<>("Enabled", Boolean.class, Mod::isEnabled),
            new TableColumn<>("Title", String.class, Mod::getTitle),
            new TableColumn<>("ID", String.class, Mod::getId),
            new TableColumn<>("Order", Integer.class, Mod::getLoadOrder),
            new TableColumn<>("Declared dependencies", Integer.class, new Function<Mod, Integer>() {
                @Override
                public Integer apply(Mod mod) {
                    return mod.getHighlanderModsConfig().getDependenciesCount();
                }
            }),
            new TableColumn<>("Declared Order Order Dependencies", Integer.class, new Function<Mod, Integer>() {
                @Override
                public Integer apply(Mod mod) {
                    return mod.getHighlanderModsConfig().getRunOrderDependenciesCount();
                }
            }),
            new TableColumn<>("WOTC", Boolean.class, Mod::isRequiresXPACK),
            new TableColumn<>("Steam ID", String.class, Mod::getPublishedFileId),
            new TableColumn<>("Updated At", Instant.class, Mod::getUpdatedAt),
            new TableColumn<>("Created At", Instant.class, Mod::getCreatedAt),
            new TableColumn<>("Size", Long.class, Mod::getSize, FileUtils::formatSizeAsMb),
            new TableColumn<>("Tags", String.class, Mod::getTags)
    };

    public ModTableModel(List<Mod> mods) {
        this.mods = mods;
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

    // Optional: get the Mod object for a given row
    public Mod getModAt(int rowIndex) {
        return mods.get(rowIndex);
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
        TableRowSorter<ModTableModel> sorter = new TableRowSorter<>(this);
        tbl.setModel(this);
        tbl.setRowSorter(sorter);
        for (int i = 0; i < columns.length; i++) {
            TableColumn column = columns[i];
            if (String.class.equals(column.type)) {
                sorter.setComparator(i, ComparatorUtils.STRING_NUMERIC_COMPARATOR);
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

    public Mod getMod(int row) {
        return mods.get(row);
    }

}