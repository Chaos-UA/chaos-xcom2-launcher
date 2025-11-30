package chaos.xcom.launcher.util;

import java.util.Comparator;

public class ComparatorUtils {

    public static final Comparator<String> STRING_NUMERIC_COMPARATOR = Comparator.nullsFirst((a, b) -> {
        try {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        } catch (Exception e) {
            return a.compareTo(b); // fallback to normal string comparison
        }
    });
}
