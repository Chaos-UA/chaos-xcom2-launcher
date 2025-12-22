package chaos.xcom.launcher.gui.SettingsDialog;

import chaos.xcom.launcher.db.property.DbProperties;
import chaos.xcom.launcher.util.OsUtils;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

@Singleton
@RequiredArgsConstructor
@Slf4j
public class SettingsService {
    private static final String STEAM_XCOM_GAME_ID = "268500";

    private final Instance<SettingsDialog> settingsDialog;
    private final DbProperties dbProps;

    public void openSettingsDialog() {
        settingsDialog.get().openSettings();
    }

    public void updateGameExe(String gameExe) {
        dbProps.gameExe.set(gameExe);
        log.info("Updated game exe: {}", gameExe);
        dbProps.gameExe.optional().ifPresent(exe -> {
            // D:\games\steam\steamapps\common\XCOM 2
            // D:\games\steam\steamapps\workshop\content\268500\...

            try {
                File gameDirFile = dbProps.getPossibleGameDir();
                if (gameDirFile == null) {
                    return;
                }
                File f = gameDirFile.getParentFile();
                if (f != null && f.getParent() != null) {
                    File steamWorkshopXcomModsDir = new File(f.getParent() + File.separator + "workshop"
                            + File.separator + "content" + File.separator + STEAM_XCOM_GAME_ID);
                    if (steamWorkshopXcomModsDir.exists()) {
                        dbProps.modDirsForSearch.addUnique(steamWorkshopXcomModsDir.getAbsolutePath());
                        log.info("Auto added steam workshop mods dir: {}", steamWorkshopXcomModsDir.getAbsolutePath());
                    }
                }
                f = dbProps.getPossibleGameModDir();
                if (f != null) {
                    dbProps.modDirsForSearch.addUnique(f.getAbsolutePath());
                    log.info("Auto added steam XCOM 2 mod dir: {}", f.getAbsolutePath());
                }
                maybeUpdateUserGameDir();
            } catch (Exception e) {
                log.warn("Failed to auto add steam mods dir by: {}", gameExe, e);
            }
        });
    }

    void maybeUpdateUserGameDir() {
        // Additional logic for non-Windows environments: check Steam Proton compatdata prefix for user config
        if (!OsUtils.IS_WINDOWS) {
            try {
                File modDir = dbProps.getPossibleGameModDir();
                if (modDir != null) {
                    // climb parents to find steamapps directory
                    File cur = modDir;
                    File steamappsDir = null;
                    while (cur != null) {
                        if (cur.getName().equalsIgnoreCase("steamapps")) {
                            steamappsDir = cur;
                            break;
                        }
                        cur = cur.getParentFile();
                    }

                    if (steamappsDir != null) {
                        // use full string with forward slashes
                        String compatRel = "compatdata/" + STEAM_XCOM_GAME_ID + "/pfx/drive_c/users/steamuser/My Documents/my games/XCOM2 War of the Chosen";
                        File compatPath = new File(steamappsDir.getAbsolutePath() + "/" + compatRel);
                        if (compatPath.isDirectory()) {
                            dbProps.userGameConfigDir.set(compatPath.getAbsolutePath());
                            log.info("Auto set userGameConfigDir to Proton prefix path: {}", compatPath.getAbsolutePath());
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("Failed to detect Proton compatdata user dir", ex);
            }
        }
    }
}
