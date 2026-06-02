package chaos.xcom.launcher.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

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

    /**
     * @param file directory.
     * @return latest modified datetime of file inside the directory recursively.
     */
    public static Optional<Instant> getLastModifiedFileInDirectory(File file) {
        if (!file.isDirectory()) {
            return Optional.empty();
        }

        try (Stream<Path> stream = Files.walk(file.toPath())) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toInstant();
                        } catch (Exception e) {
                            log.error("Failed to get last modified time for file: {}", p, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder());
        } catch (Exception e) {
            log.error("Failed to walk directory to determine last modified file: {}", file, e);
            return Optional.empty();
        }
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
