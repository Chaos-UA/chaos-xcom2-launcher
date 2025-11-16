package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.MainFormService;
import chaos.xcom.launcher.gui.SettingsDialog.SettingsDialog;
import chaos.xcom.launcher.gui.SettingsDialog.SettingsService;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

import javax.swing.*;

@Singleton
@RequiredArgsConstructor
public class MainFormMenuBar extends JMenuBar {

    private final SettingsService settingsService;
    private final MainFormService mainFormService;

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
        this.add(new JMenuItem("Exit"));
    }
}
