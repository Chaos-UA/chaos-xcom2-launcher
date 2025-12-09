package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.XImage;
import chaos.xcom.launcher.gui.component.XScrollPane;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.steam.SteamSyncProgress;
import chaos.xcom.launcher.swing.SwingService;
import chaos.xcom.launcher.util.ImageUtils;
import com.formdev.flatlaf.extras.components.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.util.List;

@Singleton
@Startup
@Slf4j
@RequiredArgsConstructor
public class MainForm extends JFrame {

    private ModTable tblModsList;
    private JPanel pnlRoot;
    private final XImage pnlModImage = new XImage();

    private final ModDeclaredDependenciesTable tblDeclaredDependencies = new ModDeclaredDependenciesTable();
    private final ModDeclaredRunOrdersTable tblDeclaredRunOrders = new ModDeclaredRunOrdersTable();
    private final ModLoadOrdersTable tblModRunOrders = new ModLoadOrdersTable();
    private final ModDependenciesTable tblModDependencies = new ModDependenciesTable();
    private FlatTextField tfModFilter;
    private CycleModIssuesPanel cycleModIssuesPanel1 = new CycleModIssuesPanel();
    private JProgressBar pbSteamProgress;
    private FlatEditorPane epSteamDescription;
    private FlatEditorPane epXcomMod;
    private JScrollPane spXcomModDesc;
    private FlatScrollPane spSteamDescription;
    private JButton btnSyncModSteamInfo;
    private final MainFormMenuBar mainFormMenuBar;
    private final SwingService swingService;
    private Mod selectedMod;

    @PostConstruct
    public void init() {
        setTitle("XCOM Chaos Mod Launcher 1.0.0");
        this.setIconImage(ImageUtils.WOTC_ICON.getImage());
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApp();
            }
        });
        this.setContentPane(pnlRoot);
        setSize(1336, 768);
        setMinimumSize(new Dimension(320, 240));
        setLocationRelativeTo(null);
        this.setJMenuBar(mainFormMenuBar);
        pbSteamProgress.setVisible(false);

        tblModsList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                // Ignore extra messages while adjusting
                if (!e.getValueIsAdjusting()) {
                    setSelectedMod(tblModsList.getSelectedMod());
                }
            }
        });

        tfModFilter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                tblModsList.setModFilter(tfModFilter.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                tblModsList.setModFilter(tfModFilter.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                tblModsList.setModFilter(tfModFilter.getText());
            }
        });

        epSteamDescription.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    try {
                        // Use Java's Desktop API to open the link in the default browser
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(new URI(e.getURL().toExternalForm()));
                        }
                    } catch (Exception ex) {
                        log.error("Failed to open link: {}", e.getURL(), ex);
                    }
                }
            }
        });

        spSteamDescription.getVerticalScrollBar().setUnitIncrement(XScrollPane.UNIT_INCREMENT);
        spXcomModDesc.getVerticalScrollBar().setUnitIncrement(XScrollPane.UNIT_INCREMENT);
        btnSyncModSteamInfo.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getModService().syncSteamMods(List.of(selectedMod));
            }
        });

        setSelectedMod(null);

        swingService.applyFullWindowState("MainForm", this);
        setVisible(true);
        SwingUtilities.invokeLater(() -> {
            getModService().reloadModsFromDirs();
        });
    }

    void exitApp() {
        log.info("Exit app");
        System.exit(0);
    }

    private ModService getModService() {
        return CDI.current().select(ModService.class).get();
    }

    public void setMods(List<Mod> parsedMods) {
        Mod currentSelectedMod = selectedMod;
        tblModsList.setMods(parsedMods);
        selectModIfExist(currentSelectedMod);
    }

    public void onMainTableDataChange() {
        Mod currentSelectedMod = selectedMod;
        tblModsList.getModel().fireTableDataChanged();
        selectModIfExist(currentSelectedMod);
    }

    private void selectModIfExist(Mod mod) {
        if (mod != null) {
            List<Mod> mods = tblModsList.getModel().getMods();
            for (int i = 0; i < mods.size(); i++) {
                if (mods.get(i).getId().equals(mod.getId())) {
                    int convertRowIndexToView = tblModsList.convertRowIndexToView(i);
                    tblModsList.getSelectionModel().setSelectionInterval(convertRowIndexToView, convertRowIndexToView);
                    tblModsList.scrollRowToVisible(convertRowIndexToView);
                    break;
                }
            }
        }
    }

    void setSelectedMod(Mod mod) {
        this.selectedMod = mod;

        tblDeclaredDependencies.setMod(mod);
        tblDeclaredRunOrders.setMod(mod);
        tblModRunOrders.setMod(mod);
        tblModDependencies.setMod(mod);
        cycleModIssuesPanel1.setMod(mod);
        btnSyncModSteamInfo.setVisible(mod != null && mod.getSteamMod().getUpdatedAt() == null);
        if (mod == null) {
            pnlModImage.setImage(ImageUtils.WOTC_ICON.getImage());
            epXcomMod.setText(null);
            epSteamDescription.setText(null);
        } else {
            pnlModImage.setImage(mod.getModPreviewImgFile());
            epXcomMod.setText(mod.getXcomModFileContent());
            epSteamDescription.setText("<html>" + StringUtils.stripToEmpty(mod.getSteamMod().getDescription()) + "</html>");
        }
        SwingUtilities.invokeLater(() -> {
            spXcomModDesc.getVerticalScrollBar().setValue(0);
            spXcomModDesc.getHorizontalScrollBar().setValue(0);
            spSteamDescription.getVerticalScrollBar().setValue(0);
            spSteamDescription.getHorizontalScrollBar().setValue(0);
        });
    }

    public void onSteamSyncProgress(SteamSyncProgress steamSyncProgress) {
        pbSteamProgress.setString("Steam " + steamSyncProgress.syncedModsCount + " / " + steamSyncProgress.totalModsToSyncCount);
        pbSteamProgress.setMinimum(0);
        pbSteamProgress.setValue(steamSyncProgress.syncedModsCount);
        pbSteamProgress.setMaximum(steamSyncProgress.totalModsToSyncCount);
        pbSteamProgress.setVisible(!steamSyncProgress.isComplete());
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        pnlRoot = new JPanel();
        pnlRoot.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        final JSplitPane splitPane1 = new JSplitPane();
        splitPane1.setDividerLocation(300);
        splitPane1.setResizeWeight(0.5);
        pnlRoot.add(splitPane1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(200, 200), null, 0, false));
        final JSplitPane splitPane2 = new JSplitPane();
        splitPane2.setOrientation(0);
        splitPane2.setResizeWeight(1.0);
        splitPane1.setLeftComponent(splitPane2);
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPane2.setLeftComponent(panel1);
        final JScrollPane scrollPane1 = new JScrollPane();
        panel1.add(scrollPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        tblModsList = new ModTable();
        tblModsList.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        scrollPane1.setViewportView(tblModsList);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPane2.setRightComponent(panel2);
        panel2.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Mod information", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final FlatSplitPane flatSplitPane1 = new FlatSplitPane();
        flatSplitPane1.setResizeWeight(0.0);
        panel2.add(flatSplitPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        flatSplitPane1.setLeftComponent(pnlModImage);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        flatSplitPane1.setRightComponent(panel3);
        final FlatTabbedPane flatTabbedPane1 = new FlatTabbedPane();
        panel3.add(flatTabbedPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        flatTabbedPane1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        flatTabbedPane1.addTab("Steam description", panel4);
        spSteamDescription = new FlatScrollPane();
        spSteamDescription.setHorizontalScrollBarPolicy(30);
        spSteamDescription.setShowButtons(false);
        spSteamDescription.setSmoothScrolling(false);
        panel4.add(spSteamDescription, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        epSteamDescription = new FlatEditorPane();
        epSteamDescription.setContentType("text/html");
        epSteamDescription.setEditable(false);
        epSteamDescription.setText("<html>\r\n  <head>\r\n    \r\n  </head>\r\n  <body>\r\n  </body>\r\n</html>\r\n");
        spSteamDescription.setViewportView(epSteamDescription);
        btnSyncModSteamInfo = new JButton();
        btnSyncModSteamInfo.setText("Sync Steam mod info");
        panel4.add(btnSyncModSteamInfo, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spXcomModDesc = new JScrollPane();
        spXcomModDesc.setHorizontalScrollBarPolicy(30);
        flatTabbedPane1.addTab("XComMod", spXcomModDesc);
        epXcomMod = new FlatEditorPane();
        epXcomMod.setEditable(false);
        epXcomMod.setMinimumSize(new Dimension(100, 23));
        epXcomMod.setText("Label");
        spXcomModDesc.setViewportView(epXcomMod);
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(4, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPane1.setRightComponent(panel5);
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel5.add(panel6, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel6.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Declared CHModDependency", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane2 = new JScrollPane();
        panel6.add(scrollPane2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        tblDeclaredDependencies.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        scrollPane2.setViewportView(tblDeclaredDependencies);
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel5.add(panel7, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel7.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Declared CHDLCRunOrder", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane3 = new JScrollPane();
        panel7.add(scrollPane3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        tblDeclaredRunOrders.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        scrollPane3.setViewportView(tblDeclaredRunOrders);
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel5.add(panel8, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel8.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Final Load Order", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane4 = new JScrollPane();
        panel8.add(scrollPane4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        tblModRunOrders.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        scrollPane4.setViewportView(tblModRunOrders);
        cycleModIssuesPanel1.putClientProperty("html.disable", Boolean.FALSE);
        panel8.add(cycleModIssuesPanel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel5.add(panel9, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel9.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Final dependencies", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane5 = new JScrollPane();
        panel9.add(scrollPane5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        tblModDependencies.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        scrollPane5.setViewportView(tblModDependencies);
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        pnlRoot.add(panel10, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        tfModFilter = new FlatTextField();
        tfModFilter.setPlaceholderText("Mod Filter");
        tfModFilter.setShowClearButton(true);
        tfModFilter.setToolTipText("Mod Filter");
        panel10.add(tfModFilter, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, new Dimension(150, -1), new Dimension(150, -1), new Dimension(150, -1), 0, false));
        pbSteamProgress = new JProgressBar();
        pbSteamProgress.setString("");
        pbSteamProgress.setStringPainted(true);
        pbSteamProgress.setValue(0);
        panel10.add(pbSteamProgress, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return pnlRoot;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }

}
