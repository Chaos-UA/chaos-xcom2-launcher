package chaos.xcom.launcher.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

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
        return new File(USER_HOME + "/Documents/My Games/XCOM2 War of the Chosen");
    }

    public static Optional<Long> calculateDirectorySize(File file) {
        if (!file.isDirectory()) {
            return Optional.empty();
        }
        return calculateDirectorySize(file.toPath());
    }

    public static Optional<Long> calculateDirectorySize(Path path)  {
        try {
            return Optional.of(Files.walk(path)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (Exception e) {
                            log.error("Failed to get file size: {}", p, e);
                            return 0L;
                        }
                    })
                    .sum());
        } catch (Exception e) {
            log.error("Failed to get directory size", e);
            return Optional.empty();
        }
    }

    public static String formatSizeAsMb(Long bytesSize) {
        return String.format("%s MB", bytesSize == null
                ? "?"
                : new BigDecimal(bytesSize / 1024.0 / 1024.0).setScale(2, RoundingMode.UP));
    }
}
