package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.TableColumn;
import chaos.xcom.launcher.gui.component.event.XTableModel;
import chaos.xcom.launcher.highlander.dto.HighlanderRunPriorityGroup;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.util.ComparatorUtils;
import chaos.xcom.launcher.util.DateUtils;
import chaos.xcom.launcher.util.FileUtils;
import jakarta.enterprise.inject.spi.CDI;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.table.TableRowSorter;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Slf4j
public class ModTableModel extends XTableModel<Mod> {

    TableColumn DEPENDENCIES_COLUMN = new TableColumn<>("Dependencies", Integer.class, new Function<Mod, Integer>() {
        @Override
        public Integer apply(Mod mod) {
            return mod.getLoadOrders().size()
                    + mod.getDependencies().size()
                    + mod.getHighlanderGroupLoadOrders().size();
        }
    });
    TableColumn DECLARED_DEPENDENCIES_COLUMN = new TableColumn<>("Declared Dependencies", Integer.class, new Function<Mod, Integer>() {
        @Override
        public Integer apply(Mod mod) {
            return mod.getDeclaredDependencies().size() + mod.getDeclaredLoadOrders().size()
                    + ModService.get().getDeclaredUserDependenciesSize(mod);
        }
    });
    TableColumn HIGHLANDER_GROUPS_COLUMN = new TableColumn<>("Highlander Group", String.class, new Function<Mod, String>() {
        @Override
        public String apply(Mod mod) {
            HighlanderRunPriorityGroup grp = mod.getHighlanderGroupLoadOrders().isEmpty()
                    ? HighlanderRunPriorityGroup.STANDARD
                    : mod.getHighlanderGroupLoadOrders().getLast().getPriorityGroup();
            String text = grp == null ? "" : grp.toString();
            if (mod.getHighlanderGroupLoadOrders().size() > 1) {
                // return HTML to enable bold rendering in JTable
                return "<html><strong>" + text + "</strong></html>";
            }
            return text;
        }
    });
    TableColumn STEAM_ID_COLUMN = new TableColumn<>("Steam ID", String.class, (Mod v) -> v.getSteamMod().getSteamModId());

    @Getter
    private List<Mod> mods;
    private String modFilter = "";
    private final TableRowSorter<ModTableModel> rowSorter;
    private ModTable modTable;


    private TableColumn[] createColumns() {
        return new TableColumn[]{
                new TableColumn<>("Active", Boolean.class, Mod::isActive),
                new TableColumn<>("Order", Integer.class, Mod::getLoadOrder, new Function<Integer, String>() {
                    @Override
                    public String apply(Integer order) {
                        return Objects.equals(order, Mod.MOD_ORDER_DISABLED) ? "" : String.valueOf(order);
                    }
                }),
                new TableColumn<>("ID", String.class, Mod::getId),
                new TableColumn<>("Title", String.class, (Mod mod) -> ModService.get().getModTitle(mod.getId())),
                new TableColumn<>("Status", String.class, Mod::getStatusAsString),
                DEPENDENCIES_COLUMN,
                DECLARED_DEPENDENCIES_COLUMN,
                HIGHLANDER_GROUPS_COLUMN,
                STEAM_ID_COLUMN,
                new TableColumn<>("Steam sync at", String.class, (Mod v) -> {
                    if (v.getSteamMod().getUpdatedAt() == null) {
                        return "?";
                    } else {
                        return DateUtils.format(v.getSteamMod().getUpdatedAt());
                    }
                }),
                new TableColumn<>("Modified at", String.class, (Mod v) -> {
                    if (v.getLastModifiedAt() == null) {
                        return "?";
                    } else {
                        return DateUtils.format(v.getLastModifiedAt());
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
                new TableColumn<>("Published File ID", String.class, Mod::getPublishedFileId),
                new TableColumn<>("Size", Long.class, Mod::getSize, FileUtils::formatSizeAsMb)
        };
    };

    public ModTableModel(List<Mod> mods) {
        this.setColumns(createColumns());
        this.mods = mods;
        this.rowSorter = new TableRowSorter<>(this);
    }

    public void setModFilter(String modFilterInput) {
        this.modFilter = StringUtils.stripToEmpty(modFilterInput).toLowerCase().replaceAll(" ", "");
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
                    return mod.getId().toLowerCase().replaceAll(" ", "").contains(modFilter)
                            || mod.getTitle().toLowerCase().replaceAll(" ", "").contains(modFilter);
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
            Mod selectedMod = modTable.getSelectedMod();
            if (selectedMod == mod) {
                modTable.clearSelection();
            }
            SwingUtilities.invokeLater(() -> {
                getModService().setModActive(mod.getId(), (boolean) aValue);
            });

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

    public void apply(ModTable tbl) {
        this.modTable = tbl;
        super.apply(tbl);
        tbl.setRowSorter(rowSorter);

        for (int i = 0; i < columns.length; i++) {
            TableColumn column = columns[i];
            if (String.class.equals(column.type)) {
                rowSorter.setComparator(i, ComparatorUtils.STRING_NUMERIC_COMPARATOR);
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
