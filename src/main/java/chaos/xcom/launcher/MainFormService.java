package chaos.xcom.launcher;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;

@Singleton
@Slf4j
public class MainFormService {

    public void exitApp() {
        int result = JOptionPane.showConfirmDialog(null,
                "Exit application?",
                "Exit confirmation",
                JOptionPane.YES_NO_OPTION);
        if (JOptionPane.YES_OPTION == result) {
            log.info("Exit app");
            System.exit(0);
        }
    }
}
