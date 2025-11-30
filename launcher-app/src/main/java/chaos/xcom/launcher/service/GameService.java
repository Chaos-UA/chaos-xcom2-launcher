package chaos.xcom.launcher.service;

import chaos.xcom.launcher.db.property.DbProperties;
import chaos.xcom.launcher.gui.MainForm.MainFormService;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.io.File;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class GameService {

    private final DbProperties dbProps;

    public void startGame() {
        String gameDir = dbProps.gameDir.optional().orElse("");
        File exeFile = new File(gameDir + "/XCom2-WarOfTheChosen/Binaries/Win64/XCom2.exe");
        if (!exeFile.exists()) {
            log.info("XCOM exe file doesn't exist: {}", exeFile);
            JOptionPane.showMessageDialog(null,
                    "XCOM exe file doesn't exist: " + exeFile.getAbsolutePath(), "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        log.info("Starting game: {}", exeFile);

            ProcessBuilder pb = new ProcessBuilder(
                    exeFile.getAbsolutePath(),
                    "--mode", "silent",
                    "--debug111"
            );
        try {
            pb.start();
            if (dbProps.exitOnGameLaunch.isTrue()) {
                log.info("Exiting after game started");
                System.exit(0);
            }
        } catch (Exception e) {
            log.error("Failed to start XCOM: {}", exeFile, e);
            JOptionPane.showMessageDialog(null,
                    "Failed to start XCOM: " + exeFile.getAbsolutePath(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
