package chaos.xcom.launcher.util;

public class Longs {

    public static Long parseLong(String string) {
        if (string == null) {
            return null;
        }
        return Long.parseLong(string);
    }

    public static String toString(Long value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}
