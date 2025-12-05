package chaos.xcom.launcher.db.property;

import chaos.xcom.launcher.service.SwingComponentStates;
import chaos.xcom.launcher.util.FileUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.formdev.flatlaf.FlatIntelliJLaf;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import java.io.File;
import java.util.List;

@Singleton
public class DbProperties extends AbstractProperties {

    public final OptionalStringProperty gameDir = optionalStringProp("gameDir", "Game directory");
    public final StringProperty userGameConfigDir = requiredStringProp("userGameConfigDir",
            FileUtils.getDefaultXCom2UserDir().getAbsolutePath(),  "User game config directory");
    public final ListProperty<String> modDirsForSearch = listProp("modDirs", new TypeReference<>() {}, List.of(), "Mods directories");
    public final IntProperty modDirMaxSubDirsForSearch = requiredIntProp("modDirMaxSubDirsForSearch", 4,
            "Max number of sub directories to go deep for while searching mods");
    public final StringProperty guiSkin = requiredStringProp("guiSkin", FlatIntelliJLaf.class.getName(), "GUI skin");
    public final ListProperty<String> gameLaunchArgs = listProp("gameLaunchArgs", new TypeReference<>() {}, List.of(), "XCOM launch arguments");
    public final BooleanProperty exitOnGameLaunch = booleanProp("exitOnGameLaunch", true, "Exit on XCOM game launch to free RAM memory resources");
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
}
