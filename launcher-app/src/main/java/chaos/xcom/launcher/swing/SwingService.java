package chaos.xcom.launcher.swing;

import chaos.xcom.launcher.db.property.DbProperties;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;

@Slf4j
@Singleton
@Startup
@RequiredArgsConstructor
public class SwingService {

    private static Window lastActiveWindow;

    private final DbProperties dbProps;
    private SwingComponentStates componentStates = new SwingComponentStates();

    public static Window getLastActiveWindowBounds() {
        return lastActiveWindow;
    }

    public static SwingService get() {
        return CDI.current().select(SwingService.class).get();
    }

    @PostConstruct
    public void loadAllStates() {
        componentStates = dbProps.swingComponentStates.get();
        log.info("Loaded Swing components states from DB properties");
    }

    @PreDestroy
    public void saveAllStates() {
        dbProps.swingComponentStates.set(componentStates);
        log.info("Saved Swing components states to DB properties");
    }

    // ---------------------------
    // Restore JFrame/JDialog + all nested JSplitPanes
    // ---------------------------
    public void applyFullWindowState(String key, Window window) {
        // 1. Restore window itself
        applyWindowState(key, window);

        // 2. Recursively find all nested JSplitPanes
        java.util.List<JSplitPane> splitPanes = findAllSplitPanes(window);

        // 3. Apply split state with auto-generated keys
        for (int i = 0; i < splitPanes.size(); i++) {
            JSplitPane sp = splitPanes.get(i);
            String spKey = buildSplitPaneKey(key, i);
            applySplitState(spKey, sp);
        }
    }

    // Recursively find all JSplitPanes inside a window/container
    private java.util.List<JSplitPane> findAllSplitPanes(Component root) {
        java.util.List<JSplitPane> result = new java.util.ArrayList<>();

        if (root instanceof JSplitPane) {
            result.add((JSplitPane) root);
        }

        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                result.addAll(findAllSplitPanes(child));
            }
        }

        return result;
    }

    public boolean isWindowVisibleOnAnyScreen(Window window) {
        java.awt.Rectangle windowRect = window.getBounds();

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (GraphicsDevice gd : ge.getScreenDevices()) {
            GraphicsConfiguration gc = gd.getDefaultConfiguration();
            java.awt.Rectangle screenBounds = gc.getBounds();

            if (screenBounds.intersects(windowRect)) {
                return true;
            }
        }
        return false;
    }

    // Generates a stable unique key for each split pane
    private String buildSplitPaneKey(String windowKey, int index) {
        return windowKey + ".split." + index;
    }

    // ---------------------------
    // Restore JFrame/JDialog
    // ---------------------------
    public void applyWindowState(String key, Window window) {
        Map<String, Rectangle> windowBounds = componentStates.getWindowBounds();
        Rectangle r = windowBounds.get(key);
        if (r != null) {
            window.setBounds(r.toAwtRectangle());
            log.debug("{} {} restored bounds: {}", window.getClass().getSimpleName(), key, r);
        }
        if (!isWindowVisibleOnAnyScreen(window)) {
            window.setLocationRelativeTo(null);
        }
        window.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                updateBounds();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                updateBounds();
            }

            void updateBounds() {
                windowBounds.put(key, Rectangle.from(window.getBounds()));
            }
        });
        window.addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                lastActiveWindow = window;
            }

            @Override
            public void windowLostFocus(WindowEvent e) {

            }
        });
    }

    // ---------------------------
    // Restore JSplitPane
    // ---------------------------
    public void applySplitState(String key, JSplitPane splitPane) {
        Map<String, Integer> splitPanePositions = componentStates.getSplitPanePositions();
        Integer pos = splitPanePositions.get(key);
        if (pos != null) {
            splitPane.setDividerLocation(pos);
            log.debug("{} {} restored divider: {}", splitPane.getClass().getSimpleName(), key, pos);
        }

        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            splitPanePositions.put(key, splitPane.getDividerLocation());
        });
    }


}