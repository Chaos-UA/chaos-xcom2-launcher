package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.SettingsDialog.SettingsService;
import chaos.xcom.launcher.service.GameService;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.util.ImageUtils;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

import javax.swing.*;

@Singleton
@RequiredArgsConstructor
public class MainFormMenuBar extends JMenuBar {

    private final SettingsService settingsService;
    private final MainFormService mainFormService;
    private final GameService gameService;
    private final Provider<ModService> modService;

    @PostConstruct
    public void init() {
        JMenu fileMenu = new JMenu("File");

        JMenuItem settingsItem = new JMenuItem("Settings");
        fileMenu.add(settingsItem);
        settingsItem.addActionListener(e -> settingsService.openSettingsDialog());

        JMenuItem syncAllSteamMods = new JMenuItem("Sync info from steam for all mods");
        fileMenu.add(syncAllSteamMods);
        syncAllSteamMods.addActionListener(e -> modService.get().SyncAllSteamMods());
        syncAllSteamMods.setToolTipText("Sync dependencies for required mods from steam workshop. "
                + "\nShould not be called often. May take a long time if you have many mods.");

        JMenuItem syncMissingSteamMods = new JMenuItem("Sync missing info from steam for mods");
        fileMenu.add(syncMissingSteamMods);
        syncMissingSteamMods.addActionListener(e -> modService.get().SyncMissingSteamMods());

        fileMenu.addSeparator();
        JMenuItem menuItem = new JMenuItem("Exit");
        fileMenu.add(menuItem);
        menuItem.addActionListener(e -> mainFormService.exitApp());


        this.add(fileMenu);
        JMenuItem item = new JMenuItem("Reload");
        item.setIcon(ImageUtils.REFRESH_ICON);
        item.setToolTipText("Scan mod directories for new/removed mods and reload mod list");
        this.add(item);
        item.addActionListener(e -> modService.get().reloadModsFromDirs());

        JMenu startXcomItem = new JMenu("Play XCOM2");
        startXcomItem.setIcon(ImageUtils.scaleIconForMenu(ImageUtils.WOTC_ICON));
        this.add(startXcomItem);
        startXcomItem.addActionListener(e -> gameService.startGame());

    }
}
