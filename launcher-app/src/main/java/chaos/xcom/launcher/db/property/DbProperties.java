package chaos.xcom.launcher.db.property;

import chaos.xcom.launcher.swing.SwingComponentStates;
import chaos.xcom.launcher.util.FileUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.formdev.flatlaf.FlatIntelliJLaf;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import java.io.File;
import java.util.List;

@Singleton
public class DbProperties extends AbstractProperties {

    public final OptionalStringProperty gameExe = optionalStringProp("gameDir", "Game directory");
    public final StringProperty userGameConfigDir = requiredStringProp("userGameConfigDir",
            FileUtils.getDefaultXCom2UserDir().getAbsolutePath(),  "User game config directory");
    public final ListProperty<String> modDirsForSearch = listProp("modDirs", new TypeReference<>() {}, List.of(), "Mods directories");
    public final IntProperty modDirMaxSubDirsForSearch = requiredIntProp("modDirMaxSubDirsForSearch", 4,
            "Max number of sub directories to go deep for while searching mods");
    public final StringProperty guiSkin = requiredStringProp("guiSkin", FlatIntelliJLaf.class.getName(), "GUI skin");
    public final ListProperty<String> gameLaunchArgs = listProp("gameLaunchArgs", new TypeReference<>() {},
            List.of("-allowconsole","-noRedscreens","-review"), "XCOM launch arguments");
    public final BooleanProperty exitOnGameLaunch = booleanProp("exitOnGameLaunch", true, "Exit on XCOM game launch to free RAM memory resources");
    public final BooleanProperty syncMissingSteamModsOnReload = booleanProp("syncMissingSteamModsOnReload", true,
            "Sync missing Steam mods on mods reload");
    public final BooleanProperty gameLogEnabled = booleanProp("gameLogEnabled", false,
            "Write log to xcom-game.log file in launcher directory");
    public final JsonProperty<SwingComponentStates> swingComponentStates = jsonProp("swingComponentStates",
            new TypeReference<>() {}, new SwingComponentStates(),
            "Swing components states. Windows positions, sizes, etc.");

    public DbProperties(Instance<PropertyService> propertyService) {
        super(propertyService);
    }

    public File getXComEngineIniFile() {
        return new File(userGameConfigDir.get() + "/XComGame/Config/XComEngine.ini");
    }

    public File getXComModOptionsIniFile() {
        return new File(userGameConfigDir.get() + "/XComGame/Config/XComModOptions.ini");
    }

    public File getPossibleGameModDir() {
        File gameExeFile = gameExe.optional().map(File::new).orElse(null);
        if (gameExeFile != null && gameExeFile.isFile()) {
            try {
                File modDir = new File(gameExeFile.getParentFile().getParentFile().getParentFile() + "/XComGame/Mods");
                if (modDir.isDirectory()) {
                    return modDir;
                }
            } catch (Exception e) {
                // no logs
            }
        }
        return null;
    }

    public File getPossibleGameDir() {
        File gameExeFile = gameExe.optional().map(File::new).orElse(null);
        if (gameExeFile != null && gameExeFile.isFile()) {
            try {
                File parentDir = gameExeFile.getParentFile();
                while (parentDir != null) { // XCOM 2/XCom2-WarOfTheChosen/Binaries/Win64/XCom2.exe
                    if (parentDir.getName().equals("XCOM 2")) {
                        return parentDir;
                    }
                    parentDir = parentDir.getParentFile();
                }

            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
