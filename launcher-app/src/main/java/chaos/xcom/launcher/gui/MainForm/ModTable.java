package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.exception.InternalException;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.gui.component.event.XMouseAdapter;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.mod.dto.ModStatus;
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
import java.net.URI;
import java.util.Arrays;
import java.util.List;

public class ModTable extends XTable {

    public ModTable() {
        super(new ModTableModel(List.of()));
        this.setModel(new ModTableModel(List.of()));

        this.addHighlighter(new AbstractHighlighter() {
            @Override
            protected Component doHighlight(Component component, ComponentAdapter adapter) {
                Mod row = getModel().getModByRawIndex(convertRowIndexToModel(adapter.row));

                if (!row.isActive()) {
                    //component.setBackground(ColorConstant.getTableBackgroundColor());
                    component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                }

                return component;
            }
        });

        addMouseListener(new XMouseAdapter() {

            @Override
            public void popUpTrigger(MouseEvent e) {
                showMenu(e);
            }

            private void showMenu(MouseEvent e) {
                if (e.isPopupTrigger()) {
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

                    List<Mod> existingMods = selectedMods.stream()
                            .filter(mod -> mod.isExist())
                            .toList();
                    if (!existingMods.isEmpty()) {
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
                    List<Mod> modsWithSteamId = selectedMods.stream()
                            .filter(mod -> StringUtils.isNotBlank(mod.getSteamMod().getSteamModId()))
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

                        // remove deleted mod from list
                        List<Mod> deletedMods = modsWithSteamId.stream()
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

                    }
                    menu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });


        this.addHighlighter(new AbstractHighlighter() {
            @Override
            protected Component doHighlight(Component component, ComponentAdapter adapter) {
                Mod mod = getModel().getModByRawIndex(convertRowIndexToModel(adapter.row));
                if (mod == null) {
                    return component;
                }

                if (!mod.getStatuses().contains(ModStatus.OK)) {
                    if (mod.isActive()) {
                        component.setBackground(ColorConstant.ERROR.getColor());
                    } else { // not ok and not active
                        component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                        if (mod.getStatuses().contains(ModStatus.DUPLICATE)) {
                            component.setBackground(ColorConstant.ERROR.getColor());
                        }
                    }
                }

                return component;
            }
        });
    }

    /**
     * Fix LookAndFill change without restarting the app
     */
    @Override
    public void updateUI() {
        super.updateUI();
        if (getModel() != null) {
            getModel().apply(this);
        }
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
