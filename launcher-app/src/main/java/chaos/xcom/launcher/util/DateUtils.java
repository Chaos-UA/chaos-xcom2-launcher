package chaos.xcom.launcher.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateUtils {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String format(Instant instant) {
        return DATE_TIME_FORMATTER.format(
                LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
    }
}
