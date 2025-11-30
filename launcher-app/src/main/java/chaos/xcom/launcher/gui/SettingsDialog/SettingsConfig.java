package chaos.xcom.launcher.gui.SettingsDialog;

import lombok.Data;

import java.io.File;
import java.util.List;

@Data
public class SettingsConfig {

    private File gameDir;
    private List<File> modDirs;
    private final int modsDirsMaxSubdirectories = 3;
    private String gameLaunchArgs;
}
