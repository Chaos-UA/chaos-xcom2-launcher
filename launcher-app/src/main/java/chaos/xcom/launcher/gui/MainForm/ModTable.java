package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.exception.InternalException;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.gui.component.event.XMouseAdapter;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.util.ColorConstant;
import jakarta.enterprise.inject.spi.CDI;
import org.apache.commons.lang3.StringUtils;
import org.jdesktop.swingx.decorator.AbstractHighlighter;
import org.jdesktop.swingx.decorator.ColorHighlighter;
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
                        for (Mod mod : selectedMods) {
                            modService.setModActive(mod.getId(), true);
                        }
                    });
                    menu.add(activateModsItem);

                    JMenuItem deactivateModsItem = new JMenuItem("Deactivate");
                    deactivateModsItem.addActionListener(ae -> {
                        ModService modService = getModService();
                        for (Mod mod : selectedMods) {
                            modService.setModActive(mod.getId(), false);
                        }
                    });
                    menu.add(deactivateModsItem);

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
                    List<Mod> modsWithSteamId = selectedMods.stream()
                            .filter(mod -> StringUtils.isNotBlank(mod.getSteamMod().getSteamModId()))
                            .toList();
                    if (!modsWithSteamId.isEmpty()) {
                        JMenuItem openSteamMod = new JMenuItem("Open mod in Steam");
                        openSteamMod.addActionListener(ae -> {
                            try {
                                for (Mod mod : modsWithSteamId) {
                                    Desktop.getDesktop().browse(new URI("https://steamcommunity.com/sharedfiles/filedetails/?id=" + mod.getPublishedFileId()));
                                }
                            } catch (Exception ex) {
                                throw new InternalException().cause(ex);
                            }
                        });
                        menu.add(openSteamMod);

                        JMenuItem syncSteamMod = new JMenuItem("Sync mod info from Steam");
                        syncSteamMod.addActionListener(ae -> {
                            getModService().SyncSteamMods(modsWithSteamId);
                        });
                        menu.add(syncSteamMod);

                    }
                    menu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });


        addHighlighter(new ColorHighlighter(
                (renderer, adapter) -> {
                    // 4. Decide whether to highlight
                    Mod mod = getModel().getModByRawIndex(convertRowIndexToModel(adapter.row));
                    return mod.isActive() && !mod.getStatuses().contains(Mod.Status.OK);
                },
                ColorConstant.MISSING_DEPENDENCY_MOD.getColor(),
                Color.WHITE
        ));

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
