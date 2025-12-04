package chaos.xcom.launcher.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OsUtils {

    public static boolean IS_WINDOWS;

    static {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            log.info("Running on Windows");
            IS_WINDOWS = true;
        } else if (os.contains("mac")) {
            log.info("Running on macOS");
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            log.info("Running on Linux/Unix");
        } else {
            log.info("Unknown OS: " + os);
        }
    }
}
