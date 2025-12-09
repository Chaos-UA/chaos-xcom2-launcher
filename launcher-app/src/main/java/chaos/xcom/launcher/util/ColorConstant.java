package chaos.xcom.launcher.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.swing.*;
import java.awt.*;

@RequiredArgsConstructor
@Getter
public enum ColorConstant {
    ERROR(new Color(255, 0, 0, 120)),
    DISABLED_MOD(new Color(255, 102, 102))
    ;


    private final Color color;

    public static Color getLabelDisabledForegroundColor() {
        return UIManager.getColor("Label.disabledForeground");
    }

}
