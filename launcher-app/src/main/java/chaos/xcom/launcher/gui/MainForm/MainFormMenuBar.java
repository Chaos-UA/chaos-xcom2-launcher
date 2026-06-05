package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.SettingsDialog.SettingsService;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.service.GameService;
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
    private final LauncherService launcherService;
    private final GameService gameService;
    private final Provider<ModService> modService;

    @PostConstruct
    public void init() {
        JMenu fileMenu = new JMenu("Launcher");

        JMenuItem settingsItem = new JMenuItem("Settings");
        fileMenu.add(settingsItem);
        settingsItem.addActionListener(e -> settingsService.openSettingsDialog());

        fileMenu.addSeparator();

        JMenuItem calculateModsSize = new JMenuItem("Calculate mods size and last modified time");
        calculateModsSize.addActionListener(e -> {
            modService.get().calculateAllModsSizeAndSave();
        });
        fileMenu.add(calculateModsSize);

        fileMenu.addSeparator();
        JMenuItem syncAllSteamMods = new JMenuItem("Sync Steam mods description and required mods all mods");
        fileMenu.add(syncAllSteamMods);
        syncAllSteamMods.addActionListener(e -> modService.get().syncAllSteamMods());
        syncAllSteamMods.setToolTipText("Sync mods description and dependencies for required mods from steam workshop");

        JMenuItem syncMissingSteamMods = new JMenuItem("Sync Steam missing mods info");
        fileMenu.add(syncMissingSteamMods);
        syncMissingSteamMods.addActionListener(e -> modService.get().syncMissingSteamMods());

        fileMenu.addSeparator();
        JMenuItem menuItem = new JMenuItem("Exit");
        fileMenu.add(menuItem);
        menuItem.addActionListener(e -> launcherService.exitApp());


        this.add(fileMenu);
        JMenuItem item = new JMenuItem("Reload");
        item.setIcon(ImageUtils.REFRESH_ICON);
        item.setToolTipText("Scan mod directories for new/removed mods and reload mod list");
        this.add(item);
        item.addActionListener(e -> modService.get().reloadModsFromDirs());

        JMenuItem startXcomItem = new JMenuItem("Play XCOM2");
        startXcomItem.setIcon(ImageUtils.scaleIconForMenu(ImageUtils.WOTC_ICON));
        startXcomItem.setToolTipText("Play XCOM2 with active mods");
        this.add(startXcomItem);
        startXcomItem.addActionListener(e -> gameService.startGame());

    }
}
