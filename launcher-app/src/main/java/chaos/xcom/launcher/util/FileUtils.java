package chaos.xcom.launcher.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class FileUtils {
    public static final File USER_HOME = new File(System.getProperty("user.home"));
    public static final File WORKING_DIRECTORY = new File(System.getProperty("user.dir")).getAbsoluteFile();
    public static final File LAUNCHER_ACTIVE_MODS_DIRECTORY = new File(WORKING_DIRECTORY, "launcher-active-mods");

    static {
        log.info("WORKING DIRECTORY: {}", WORKING_DIRECTORY);
        log.info("LAUNCHER_ACTIVE_MODS_DIRECTORY: {}", LAUNCHER_ACTIVE_MODS_DIRECTORY);
    }

    public static File getDefaultXCom2UserDir() {
        return new  File(USER_HOME + "/Documents/My Games/XCOM2 War of the Chosen");
    }

    public static long getDirectorySize(Path path)  {
        try {
            return Files.walk(path)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (Exception e) {
                            log.error("Failed to get file size: {}", p, e);
                            return 0L;
                        }
                    })
                    .sum();
        } catch (Exception e) {
            log.error("Failed to get directory size", e);
            return 0;
        }
    }

    public static String formatSizeAsMb(long bytesSize) {
        return String.format("%s MB", new BigDecimal(bytesSize / 1024.0 / 1024.0)
                .setScale(2, RoundingMode.UP));
    }
}
