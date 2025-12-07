package chaos.xcom.launcher.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class XComUtils {

    public static final String STEAM_XCOM_GAME_ID = "268500";

    public static boolean isXcomWorkshopFolder(File file) {
        try {
            Path path = file.toPath().toAbsolutePath().normalize();

            Path suffix = Paths.get("workshop", "content", STEAM_XCOM_GAME_ID);

            // Count how many name elements we compare:
            int suffixCount = suffix.getNameCount();
            int pathCount = path.getNameCount();

            if (pathCount < suffixCount) return false;

            // Compare the last elements one-by-one:
            for (int i = 1; i <= suffixCount; i++) {
                if (!path.getName(pathCount - i).equals(suffix.getName(suffixCount - i))) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String parseModIdFromSteamWorkshopDir(File modDir) {
        try {
            if (!isXcomWorkshopFolder(modDir.getParentFile())) {
                return null;
            }
            return String.valueOf(Long.parseLong(modDir.getName()));
        } catch (Exception e) {
            return null;
        }
    }

}
