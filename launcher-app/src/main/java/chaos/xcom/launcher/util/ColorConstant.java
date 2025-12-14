package chaos.xcom.launcher.util;

import javax.swing.*;
import java.awt.*;


public class ColorConstant {
    public static final Color ERROR = new Color(255, 123, 123);
    public static final Color NEW_MOD = new Color(206, 255, 206);

    public static Color getLabelDisabledForegroundColor() {
        return UIManager.getColor("Label.disabledForeground");
    }

}
