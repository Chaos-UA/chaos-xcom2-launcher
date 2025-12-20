package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.TableColumn;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.gui.component.event.XMouseAdapter;
import chaos.xcom.launcher.gui.component.event.XTableModel;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.DeclarationSource;
import chaos.xcom.launcher.mod.dto.DependencyType;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.mod.dto.ModDeclaredDependency;
import chaos.xcom.launcher.steam.SteamMod.SteamRequiredMod;
import chaos.xcom.launcher.steam.SteamService;
import chaos.xcom.launcher.util.ColorConstant;
import lombok.extern.slf4j.Slf4j;
import org.jdesktop.swingx.decorator.AbstractHighlighter;
import org.jdesktop.swingx.decorator.ComponentAdapter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class ModDeclaredDependenciesTable extends XTable {
    private static final int TARGET_MOD_ID_COLUMN_INDEX = 2;
    private static final int IGNORE_COLUMN_INDEX = 4;
    private ModDependenciesTableModel model;
    private Mod mod;

    public ModDeclaredDependenciesTable() {
        this.model = new ModDependenciesTableModel();
        setSortable(false);
        model.apply(this);
        setMod(null);

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
                    ModService.get().openUserModRulesEditorDialog(mod);
                });
                menu.add(editUserModRules);

                int selectedRow = rowAtPoint(e.getPoint());
                if (selectedRow >= 0) {
                    setRowSelectionInterval(selectedRow, selectedRow);
                    ModDeclaredDependency row = model.getRows().get(selectedRow);
                    if (row.getSources().contains(DeclarationSource.HIGHLANDER)) {
                        File xcomGameIniFile = new File(mod.getDirectory() + "/Config/XComGame.ini");
                        if (xcomGameIniFile.isFile()) {
                            JMenuItem openXcomGameIniFile = new JMenuItem("Open Config/XComGame.ini of " + mod.getId());
                            openXcomGameIniFile.addActionListener(ae -> {
                                try {
                                    Desktop.getDesktop().open(xcomGameIniFile);
                                } catch (IOException ex) {
                                    log.error("Failed to open file: " + xcomGameIniFile, ex);
                                }
                            });
                            menu.add(openXcomGameIniFile);
                        }
                    }
                    SteamRequiredMod steamRequiredMod = row.getSteamRequiredMod();
                    if (steamRequiredMod != null && steamRequiredMod.getSteamModId() != null) {
                        JMenuItem browseSteamMod = new JMenuItem("Browse Steam mod - " + steamRequiredMod.getSteamModName());
                        browseSteamMod.addActionListener(ae -> SteamService.openSteamModInBrowser(steamRequiredMod.getSteamModId()));
                        menu.addSeparator();
                        menu.add(browseSteamMod);
                    }
                }

                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        this.addHighlighter(new AbstractHighlighter() {
            @Override
            protected Component doHighlight(Component component, ComponentAdapter adapter) {
                ModDeclaredDependency row = model.getRows().get(convertRowIndexToModel(adapter.row));
                if (row == null) {
                    return component;
                }
                // if user marked this declared dependency as ignored, show as disabled and don't show errors
                if (row.isIgnored()) {
                    component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                    return component;
                }
                if (row.isHasError()) {
                    component.setBackground(ColorConstant.ERROR);
                }
                if (row.getTargetMod() == null && adapter.column == TARGET_MOD_ID_COLUMN_INDEX) {
                    component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                } else if (adapter.column == IGNORE_COLUMN_INDEX) {
                    JCheckBox checkBox = (JCheckBox) component;
                    checkBox.setEnabled(row.getDependencyType() != DependencyType.REPLACED);
                }
                return component;
            }
        });
    }

    public void setMod(Mod mod) {
        this.mod = mod;
        model.setMod(mod);
        model.apply(this);
        packAll();
    }

    public class ModDependenciesTableModel extends XTableModel<ModDeclaredDependency> {

        public ModDependenciesTableModel() {
            super(createTableColumns());
        }

        private static TableColumn[] createTableColumns() {
            return new TableColumn[]{
                    new TableColumn<>("Mod ID", String.class, ModDeclaredDependency::getMod),
                    new TableColumn<>("Dependency", DependencyType.class, ModDeclaredDependency::getDependencyType),
                    new TableColumn<>("Target Mod ID", String.class, new Function<ModDeclaredDependency, String>() {
                        @Override
                        public String apply(ModDeclaredDependency declaredDependency) {
                            String targetMod = declaredDependency.getTargetMod();
                            if (targetMod == null) {
                                SteamRequiredMod steamRequiredMod = declaredDependency.getSteamRequiredMod();
                                if (steamRequiredMod != null && steamRequiredMod.getSteamModId() != null) {
                                    return steamRequiredMod.getSteamModName() + " - [" + steamRequiredMod.getSteamModId() + "]";
                                }
                                return "";
                            }
                            return targetMod;
                        }
                    }),
                    new TableColumn<>("Source", String.class, new Function<ModDeclaredDependency, String>() {
                        @Override
                        public String apply(ModDeclaredDependency declarationSource) {
                            return declarationSource.getSources().stream().map(v -> v.name()).collect(Collectors.joining(", "));
                        }
                    }),
                    new TableColumn<>("Ignore", Boolean.class, ModDeclaredDependency::isIgnored),
            };
        }

        public void setMod(Mod mod) {
            if (mod == null) {
                rows = new ArrayList<>();
            } else {
                rows = mod.getDeclaredDependencies();
            }
        }
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= rows.size()) return false;
            ModDeclaredDependency row = rows.get(rowIndex);
            // Ignore column editable only when dependency is not REPLACED
            if (columnIndex == IGNORE_COLUMN_INDEX) {
                return row.getDependencyType() != DependencyType.REPLACED;
            }
            return true;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= rows.size()) {
                return;
            }
            ModDeclaredDependency row = rows.get(rowIndex);
            if (columnIndex == IGNORE_COLUMN_INDEX) {
                boolean ignored = (boolean) aValue;
                // delegate to service to persist and recalculate
                ModService.get().setIgnoreDependency(mod, row, ignored);
            } else {
                super.setValueAt(aValue, rowIndex, columnIndex);
            }
        }
    }
}
