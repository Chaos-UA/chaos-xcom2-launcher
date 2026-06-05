package chaos.xcom.launcher.gui.MainForm;


import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.gui.component.event.XMouseAdapter;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.*;
import chaos.xcom.launcher.steam.SteamService;
import chaos.xcom.launcher.swing.SwingService;
import chaos.xcom.launcher.util.ColorConstant;
import com.codedisaster.steamworks.SteamUGC;
import jakarta.enterprise.inject.spi.CDI;
import org.apache.commons.lang3.StringUtils;
import org.jdesktop.swingx.decorator.AbstractHighlighter;
import org.jdesktop.swingx.decorator.ComponentAdapter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ModTable extends XTable {
    private final ModTableModel model;

    public ModTable() {
        super(new ModTableModel(List.of()));
        this.setModel(new ModTableModel(List.of()));
        this.model = this.getModel();

        getModel().apply(this);

        addMouseListener(new XMouseAdapter() {

            @Override
            public void popUpTrigger(MouseEvent e) {
                showMenu(e);
            }

            private void showMenu(MouseEvent e) {
                List<Mod> selectedMods = Arrays.stream(getSelectedRows()).mapToObj(v -> getModByIndex(v)).toList();
                if (selectedMods.isEmpty()) {
                    return;
                }

                JPopupMenu menu = new JPopupMenu();

                JMenuItem activateModsItem = new JMenuItem("Activate");
                activateModsItem.addActionListener(ae -> {
                    ModService modService = getModService();
                    modService.setModsActive(selectedMods, true);
                });
                menu.add(activateModsItem);

                JMenuItem deactivateModsItem = new JMenuItem("Deactivate");
                deactivateModsItem.addActionListener(ae -> {
                    ModService modService = getModService();
                    modService.setModsActive(selectedMods, false);
                });
                menu.add(deactivateModsItem);
                menu.addSeparator();

                List<Mod> existingModDirs = selectedMods.stream()
                        .filter(mod -> mod.getDirectory() != null && mod.getDirectory().isDirectory())
                        .toList();
                if (!existingModDirs.isEmpty()) {
                    JMenuItem openDir = new JMenuItem("Open mod directory");
                    openDir.addActionListener(ae -> {
                        try {
                            for (Mod mod : selectedMods) {
                                Desktop.getDesktop().open(mod.getDirectory());
                            }
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                    menu.add(openDir);
                }
                menu.addSeparator();

                if (selectedMods.size() == 1) {
                    Mod mod = selectedMods.get(0);
                    if (mod.getSteamModIdByDirName() == null) {
                        JMenuItem changeSteamModIdItem = new JMenuItem("Manually set Steam workshop mod ID");
                        changeSteamModIdItem.addActionListener(ae -> {
                            String input = JOptionPane.showInputDialog(SwingService.getLastActiveWindowBounds(),
                                    "Please enter correct Steam workshop mod ID.\nEmpty means reset to default",
                                    mod.getSteamMod().getSteamModId());
                            input = StringUtils.trimToNull(input);
                            if (input == null) {
                                return;
                            }
                            getModService().setSteamModId(mod, Long.parseLong(input));
                        });
                        menu.add(changeSteamModIdItem);
                        menu.addSeparator();
                    }

                    JMenuItem editUserModRules = new JMenuItem("Edit user mod rules");
                    editUserModRules.addActionListener(ae -> {
                        getModService().openUserModRulesEditorDialog(mod);
                    });
                    menu.add(editUserModRules);

                    JMenuItem modifyAliasSteamModIdItem = new JMenuItem("Edit Steam ID aliases");
                    modifyAliasSteamModIdItem.addActionListener(ae -> {
                        getModService().openEditUserModAliasesEditorDialog(mod, null);
                    });
                    menu.add(modifyAliasSteamModIdItem);
                    menu.addSeparator();
                }

                List<Mod> modsWithSteamId = selectedMods.stream()
                        .filter(mod -> mod.getSteamMod().getSteamModId() != null)
                        .toList();
                if (!modsWithSteamId.isEmpty()) {
                    JMenuItem openSteamMod = new JMenuItem("Open mod in Steam");
                    openSteamMod.addActionListener(ae -> {
                        SteamService.openSteamModsInBrowser(modsWithSteamId.stream()
                                .map(v -> v.getSteamMod().getSteamModId()).toList());
                    });
                    menu.add(openSteamMod);

                    JMenuItem syncSteamMod = new JMenuItem("Sync mod info from Steam");
                    syncSteamMod.addActionListener(ae -> {
                        getModService().syncSteamMods(modsWithSteamId);
                    });
                    menu.add(syncSteamMod);

                    JMenuItem menuItem = new JMenuItem("Update/Download Steam mod");
                    menuItem.addActionListener(ae -> {
                        getModService().downloadSteamMods(modsWithSteamId);
                    });
                    menu.add(menuItem);

                    boolean anyModNotSubscribed = modsWithSteamId.stream().anyMatch(m -> !m.getSteamMod().getStates().contains(SteamUGC.ItemState.Subscribed));
                    if (anyModNotSubscribed) {
                        menuItem = new JMenuItem("Subscribe Steam mods");
                        menuItem.addActionListener(ae -> {
                            getModService().subscribeSteamMods(modsWithSteamId);
                        });
                        menu.add(menuItem);
                    }

                    boolean anyModSubscribed = modsWithSteamId.stream().anyMatch(m -> m.getSteamMod().getStates().contains(SteamUGC.ItemState.Subscribed));
                    if (anyModSubscribed) {
                        menuItem = new JMenuItem("Unsubscribe Steam mods");
                        menuItem.addActionListener(ae -> {
                            getModService().unsubscribeSteamMods(modsWithSteamId);
                        });
                        menu.add(menuItem);
                    }
                }

                if (selectedMods.size() == 1) {
                    Mod mod = selectedMods.get(0);
                    if (mod.getDirectory().exists()) {
                        JMenuItem menuItem = new JMenuItem("Delete mod directory");
                        menuItem.addActionListener(ae -> {
                            int result = JOptionPane.showConfirmDialog(SwingService.getLastActiveWindowBounds(),
                                    "Are you sure want to delete this mod directory?\n" + mod.getDirectory().getAbsolutePath(),
                                    "Exit confirmation",
                                    JOptionPane.YES_NO_OPTION);
                            if (JOptionPane.YES_OPTION == result) {
                                try {
                                    getModService().deleteModDir(mod);
                                } catch (Exception ex) {
                                    JOptionPane.showMessageDialog(SwingService.getLastActiveWindowBounds(), "Error deleting mod directory: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                                }
                            }

                        });
                        menu.add(menuItem);
                    }
                }

                // remove deleted mod from list
                List<Mod> deletedMods = selectedMods.stream()
                        .filter(mod -> !mod.isExist())
                        .toList();

                if (!deletedMods.isEmpty()) {
                    JMenuItem removeDeletedMods = new JMenuItem("Remove deleted mod from list");
                    removeDeletedMods.addActionListener(ae -> {
                        ModService modService = getModService();
                        modService.removeDeletedMods(deletedMods);
                    });
                    menu.add(removeDeletedMods);
                }
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });


        this.addHighlighter(new AbstractHighlighter() {
            @Override
            protected Component doHighlight(Component component, ComponentAdapter adapter) {
                Mod mod = getModel().getModByRawIndex(convertRowIndexToModel(adapter.row));
                int colIndex = convertColumnIndexToModel(adapter.column);
                if (mod == null) {
                    return component;
                }

                Color originalBackgroundColor = component.getBackground();

                if (!mod.getStatuses().contains(ModStatus.OK)) {
                    if (mod.isActive()) {
                        component.setBackground(ColorConstant.ERROR);
                    } else { // not ok and not active
                        component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                        if (mod.getStatuses().contains(ModStatus.DUPLICATE)) {
                            component.setBackground(ColorConstant.ERROR);
                        } else if (mod.isNewMod()) {
                            component.setForeground(ColorConstant.NEW_MOD);
                        }
                    }
                }

                boolean hasIgnoredDeclaredDependency = mod.getDeclaredDependencies().stream()
                        .anyMatch(ModDeclaredDependency::isIgnored);
                boolean hasIgnoredFinalDependency = mod.getDependencies().stream()
                        .anyMatch(ModDependency::isIgnored);
                boolean hasUserFinalDependency = mod.getDependencies().stream()
                        .anyMatch(v -> v.getSources().contains(DeclarationSource.USER));
                if (colIndex == model.getColumnIndex(model.DEPENDENCIES_COLUMN) // Final dependencies
                        && (hasIgnoredFinalDependency || hasUserFinalDependency)) {
                    JLabel lbl = (JLabel) component;
                    lbl.setText("<html><strong>" + lbl.getText() + "</strong></html>");
                } else if (colIndex == model.getColumnIndex(model.DECLARED_DEPENDENCIES_COLUMN)  // Declared dependencies
                        && (hasIgnoredDeclaredDependency || getModService().hasDeclaredUserDependency(mod))) {
                    JLabel lbl = (JLabel) component;
                    lbl.setText("<html><strong>" + lbl.getText() + "</strong></html>");
                } else if (colIndex == model.getColumnIndex(model.HIGHLANDER_GROUPS_COLUMN)) { // Highlander group column
                    JLabel lbl = (JLabel) component;
                    if (mod.getHighlanderGroupLoadOrders().isEmpty()) {
                        lbl.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                    }
                } else if (colIndex == model.getColumnIndex(model.STEAM_ID_COLUMN)) { // STEAM MOD ID COLUMN
                    if (!Objects.equals(mod.getSteamModIdByDirName(), mod.getSteamDbModId())) {
                        JLabel lbl = (JLabel) component;
                        lbl.setText("<html><strong>" + lbl.getText() + "</strong></html>");
                    }
                }

                if (adapter.isSelected() && !originalBackgroundColor.equals(component.getBackground())) {
                    component.setBackground(component.getBackground().darker());
                }

                return component;
            }
        });
    }

    public ModTableModel getModel() {
        return (ModTableModel) super.getModel();
    }

    public Mod getModByIndex(int modIndex) {
        return getModel().getModByRawIndex(convertRowIndexToModel(modIndex));
    }

    public Mod getSelectedMod() {
        int row = getSelectedRow();
        if (row != -1) {
            return getModel().getModByRawIndex(convertRowIndexToModel(row));
        }
        return null;
    }

    public void setModFilter(String modFilter) {
        getModel().setModFilter(modFilter);
    }

    public void setMods(List<Mod> parsedMods) {
        List<Mod> modsBefore = getModel().getMods();
        getModel().setMods(parsedMods);
        if (modsBefore.isEmpty()) {
            packAll(); // pack once after mods not empty
        }
    }

    ModService getModService() {
        return CDI.current().select(ModService.class).get();
    }
}
