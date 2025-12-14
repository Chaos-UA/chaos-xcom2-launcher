package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.swing.SwingService;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;

@Singleton
@Slf4j
public class LauncherService {

    public void exitApp() {
        int result = JOptionPane.showConfirmDialog(SwingService.getLastActiveWindowBounds(),
                "Exit application?",
                "Exit confirmation",
                JOptionPane.YES_NO_OPTION);
        if (JOptionPane.YES_OPTION == result) {
            log.info("Exit app");
            System.exit(0);
        }
    }
}
