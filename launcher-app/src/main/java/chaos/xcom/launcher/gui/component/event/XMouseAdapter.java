package chaos.xcom.launcher.gui.component.event;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public abstract class XMouseAdapter extends MouseAdapter {

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
            popUpTrigger(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
            popUpTrigger(e);
        }
    }

    public void popUpTrigger(MouseEvent e) {

    }
}
