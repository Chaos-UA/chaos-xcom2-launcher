package chaos.xcom.launcher.gui;

import chaos.xcom.launcher.db.property.DbProperties;
import chaos.xcom.launcher.swing.SwingService;
import com.formdev.flatlaf.*;
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

@Singleton
@Startup
@RequiredArgsConstructor
@Slf4j
public class LookAndFeelService {

    @Getter
    private final List<String> lookAndFeels = new ArrayList<>();
    private final DbProperties dbProps;
    private final Instance<JFrame> frameInstance;

    @PostConstruct
    public void init() {
        appendLookAndFeel(FlatIntelliJLaf.class.getName());
        appendLookAndFeel(FlatLightLaf.class.getName());

        appendLookAndFeel(FlatDarculaLaf.class.getName());
        appendLookAndFeel(FlatDarkLaf.class.getName());

        appendLookAndFeel(FlatMacLightLaf.class.getName());
        appendLookAndFeel(FlatMacDarkLaf.class.getName());

        for (FlatAllIJThemes.FlatIJLookAndFeelInfo feelInfo : FlatAllIJThemes.INFOS) {
            appendLookAndFeel(feelInfo.getClassName());
        }

        for (UIManager.LookAndFeelInfo lookAndFeelInfo : UIManager.getInstalledLookAndFeels()) {
            appendLookAndFeel(lookAndFeelInfo.getClassName());
        }

        lookAndFeels.sort(String::compareTo);
        applyLookAndFeel(getCurrentLookAndFeel());
    }

    public void applyLookAndFeel(String className) {
        try {
            UIManager.setLookAndFeel(className);
            dbProps.guiSkin.set(className);
            log.info("Applied applied GUI skin: {}", className);
            SwingUtilities.invokeLater(() -> {
                for (JFrame frame : frameInstance) {
                    SwingUtilities.updateComponentTreeUI(frame);
                    log.info("Updated component tree UI: {}", frame.getClass().getName());
                }
            });
        } catch (Exception e) {
            log.error("Failed to apply GUI skin: {}", className, e);
            JOptionPane.showMessageDialog(SwingService.getLastActiveWindowBounds(),
                    "Failed to apply GUI skin: " + className, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void appendLookAndFeel(String lookAndFeel) {
        lookAndFeels.add(lookAndFeel);
    }

    public String getCurrentLookAndFeel() {
        return dbProps.guiSkin.get();
    }
}
