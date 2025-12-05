package chaos.xcom.launcher.service;

import chaos.xcom.launcher.db.property.DbProperties;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Map;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class SwingService {

    private final DbProperties dbProps;
    private SwingComponentStates componentStates = new SwingComponentStates();

    @PostConstruct
    public void init() {
        loadAllStates();
    }

    public void loadAllStates() {
        componentStates = dbProps.swingComponentStates.get();
        log.info("Loaded Swing components states from DB properties");
    }

    public void saveAllStates() {
        dbProps.swingComponentStates.set(componentStates);
        log.info("Saved Swing components states to DB properties");
    }

    // ---------------------------
    // Restore JFrame/JDialog
    // ---------------------------
    public void applyWindowState(String key, Window window) {
        Map<String, Rectangle> windowBounds = componentStates.getWindowBounds();
        Rectangle r = windowBounds.get(key);
        if (r != null) {
            window.setBounds(r);
            log.debug("{} {} restored bounds: {}", window.getClass().getSimpleName(), key, r);
        }

        window.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                windowBounds.put(key, window.getBounds());
            }

            @Override
            public void componentResized(ComponentEvent e) {
                windowBounds.put(key, window.getBounds());
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