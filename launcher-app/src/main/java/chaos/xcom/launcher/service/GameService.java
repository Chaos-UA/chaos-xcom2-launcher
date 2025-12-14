package chaos.xcom.launcher.service;

import chaos.xcom.launcher.db.property.DbProperties;
import chaos.xcom.launcher.exception.InternalException;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.swing.SwingService;
import chaos.xcom.launcher.util.FileUtils;
import chaos.xcom.launcher.util.OsUtils;
import chaos.xcom.launcher.util.XComIniUtils;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;

import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
@RequiredArgsConstructor
public class GameService {

    private final DbProperties dbProps;
    private Process process;

    public synchronized void startGame() {
        File exeFile = new File(dbProps.gameExe.optional().orElse(""));
        if (!exeFile.exists()) {
            log.info("XCOM exe file doesn't exist: {}", exeFile);
            JOptionPane.showMessageDialog(SwingService.getLastActiveWindowBounds(),
                    "XCOM exe file doesn't exist: " + exeFile.getAbsolutePath(), "Error",
                    JOptionPane.ERROR_MESSAGE);
            process.destroy();
            return;
        }
        if (process != null && process.isAlive()) {
            log.info("XCOM game is already running with PID: {}", process.pid());
            int result = JOptionPane.showConfirmDialog(SwingService.getLastActiveWindowBounds(),
                    "XCOM game is already running. PID: " + process.pid()
                            + ".\nKill process and start?", "Info",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                process.destroy();
                try {
                    process.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for XCOM process to terminate", e);
                    process.destroyForcibly();
                    try {
                        process.waitFor(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ex) {
                        log.error("Interrupted while waiting for XCOM process to terminate forcibly", ex);
                        JOptionPane.showMessageDialog(SwingService.getLastActiveWindowBounds(),
                                "Failed to terminate XCOM process forcibly. PID: " + process.pid(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }
                log.info("Killed XCOM game process with PID: {}", process.pid());
            } else {
                log.info("Aborted XCOM game start since it's already running");
                return;
            }
        }
        prepareModsBeforeGameStart();

        try {
            log.info("Starting game: {}", exeFile);
            List<String> exeCommands = new ArrayList<>();
            exeCommands.add(exeFile.getAbsolutePath());
            exeCommands.addAll(dbProps.gameLaunchArgs.get());

            ProcessBuilder pb = new ProcessBuilder(exeCommands);
            if (dbProps.gameLogEnabled.isTrue()) {
                pb.redirectOutput(new File("xcom-game.log").getAbsoluteFile());
                pb.redirectError(new File("xcom-game.log").getAbsoluteFile());
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            }
            pb.directory(exeFile.getParentFile());
            process = pb.start();

            if (dbProps.exitOnGameLaunch.isTrue()) {
                log.info("Exiting after game started");
                System.exit(0);
            }
        } catch (Exception e) {
            log.error("Failed to start XCOM: {}", exeFile, e);
            JOptionPane.showMessageDialog(SwingService.getLastActiveWindowBounds(),
                    "Failed to start XCOM: " + exeFile.getAbsolutePath() +
                            "\nError: " + e, "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public void prepareModsBeforeGameStart() {
        Collection<Mod> mods = getModService().getActiveMods();
        prepareModsDirectory(mods);
        prepareUserGameConfigsForMods(mods);
    }

    void prepareUserGameConfigsForMods(Collection<Mod> mods) {
        try {
            File targetTempModsDir = FileUtils.LAUNCHER_ACTIVE_MODS_DIRECTORY;
            log.info("Preparing {} mods config for directory: {}", mods.size(), targetTempModsDir);
            List<String> modIds = mods.stream().map(Mod::getId).toList();
            XComIniUtils.overwriteSection(
                    dbProps.getXComEngineIniFile(),
                    "[Engine.DownloadableContentEnumerator]",
                    List.of("ModRootDirs=" + targetTempModsDir.getAbsolutePath() + "\\"));
            XComIniUtils.overwriteSection(
                    dbProps.getXComModOptionsIniFile(),
                    "[Engine.XComModOptions]",
                    modIds.stream().map(modId -> "ActiveMods=" + modId).toList());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(SwingService.getLastActiveWindowBounds(),
                    "Failed to prepare configs in:\n" + dbProps.userGameConfigDir.get()
                            + "\nError:\n" + e, "Error",
                    JOptionPane.ERROR_MESSAGE);
            throw e;
        }
    }

    void prepareModsDirectory(Collection<Mod> mods) {
        File targetTempModsDir = FileUtils.LAUNCHER_ACTIVE_MODS_DIRECTORY;
        log.info("Preparing {} mods directory: {}", mods.size(), targetTempModsDir);
        deleteAllLinksFromDir(targetTempModsDir);
        for (Mod mod : mods) {
            String paddedOrderNumber = String.format("%05d_", mod.getLoadOrder());
            File modDir = mod.getDirectory();
            File targetDir = new File(targetTempModsDir + "/" + paddedOrderNumber + mod.getId());
            createLink(modDir, targetDir);
        }
    }

    ModService getModService() {
        return CDI.current().select(ModService.class).get();
    }

    void deleteAllLinksFromDir(File targetDir) {
        targetDir.mkdir();
        if (!targetDir.isDirectory()) {
            JOptionPane.showMessageDialog(SwingService.getLastActiveWindowBounds(),
                    "Invalid temp mods directory:\n" + targetDir.getAbsolutePath(), "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        for (File f : ObjectUtils.defaultIfNull(targetDir.listFiles(), new File[0])) {
            deleteLink(f);
        }
    }

    void deleteLink(File targetLinkPath) {
        log.info("Deleting link: {}", targetLinkPath);
        try {
            Files.delete(targetLinkPath.toPath());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(SwingService.getLastActiveWindowBounds(),
                    "Failed to delete link:" + targetLinkPath, "Error",
                    JOptionPane.ERROR_MESSAGE);
            throw new InternalException("Failed to delete link: " + targetLinkPath).cause(e);
        }
    }

    void createLink(File existingDir, File targetLinkPath) {
        try {
            if (OsUtils.IS_WINDOWS) {
                // symbolic link creating requires admin privileges, lets try to create junction link
                try {
                    Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J",
                            targetLinkPath.getAbsolutePath(), existingDir.getAbsolutePath())
                            .inheritIO()
                            .start();
                    int result = process.waitFor();
                    if (result != 0) {
                        throw new InternalException("Junction link creation exit code error: " + result);
                    }
                    return;
                } catch (Exception e) {
                    log.error("Failed to create junction link: {}. Error: {}. Fallback to symbolic link creation",
                            targetLinkPath, e.toString());
                }
            }
            Files.createSymbolicLink(targetLinkPath.toPath(), existingDir.toPath());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(SwingService.getLastActiveWindowBounds(),
                    "Failed to create symbolic link for the mod:\n" + existingDir.getAbsolutePath()
                            + "\nat this location:\n" + targetLinkPath.getAbsolutePath()
                            + "\nError: " + e, "Error",
                    JOptionPane.ERROR_MESSAGE);
            log.error("Failed to create symbolic link: {} for: {}", targetLinkPath, existingDir, e);
            throw new InternalException().cause(e);
        }
    }
}
