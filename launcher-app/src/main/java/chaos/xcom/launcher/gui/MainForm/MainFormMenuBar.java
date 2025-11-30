package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.SettingsDialog.SettingsService;
import chaos.xcom.launcher.service.GameService;
import chaos.xcom.launcher.mod.ModService;
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

        fileMenu.addSeparator();
        JMenuItem menuItem = new JMenuItem("Exit");
        fileMenu.add(menuItem);
        menuItem.addActionListener(e -> mainFormService.exitApp());


        this.add(fileMenu);
        JMenuItem item = new JMenuItem("Scan mod directories");
        this.add(item);
        item.addActionListener(e -> modService.get().reloadModsFromDirs());

        JMenuItem startXcomItem = new JMenuItem("Play XCOM2");
        this.add(startXcomItem);
        startXcomItem.addActionListener(e -> gameService.startGame());

    }
}
