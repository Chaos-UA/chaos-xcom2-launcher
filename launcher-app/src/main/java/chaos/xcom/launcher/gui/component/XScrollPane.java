package chaos.xcom.launcher.gui.component;

import com.formdev.flatlaf.extras.components.FlatScrollPane;

public class XScrollPane extends FlatScrollPane {

    public static final int UNIT_INCREMENT = 8;

    public XScrollPane() {
        this.getVerticalScrollBar().setUnitIncrement(UNIT_INCREMENT);
        this.getHorizontalScrollBar().setUnitIncrement(UNIT_INCREMENT);
    }
}
