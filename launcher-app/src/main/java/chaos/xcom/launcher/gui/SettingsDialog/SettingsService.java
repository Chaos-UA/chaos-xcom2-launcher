package chaos.xcom.launcher.gui.SettingsDialog;

import chaos.xcom.launcher.db.property.DbProperties;
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
            } catch (Exception e) {
                log.warn("Failed to auto add steam mods dir by: {}", gameExe, e);
            }
        });
    }


}
