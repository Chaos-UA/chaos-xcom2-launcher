package chaos.xcom.launcher.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.awt.*;

@RequiredArgsConstructor
@Getter
public enum ColorConstant {
    MISSING_DEPENDENCY_MOD(new Color(255, 0, 0, 120)),
    DISABLED_MOD(new Color(106, 106, 106, 61))
    ;


    private final Color color;

}
