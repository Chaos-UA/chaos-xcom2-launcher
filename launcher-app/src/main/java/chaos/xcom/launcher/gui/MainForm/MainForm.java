package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.XImage;
import chaos.xcom.launcher.gui.component.XScrollPane;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.mod.dto.ModStatus;
import chaos.xcom.launcher.steam.SteamSyncProgress;
import chaos.xcom.launcher.swing.SwingService;
import chaos.xcom.launcher.util.ImageUtils;
import com.formdev.flatlaf.extras.components.*;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import jakarta.enterprise.inject.spi.CDI;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class MainForm extends JFrame {

    private static final int TAB_XCOM_GAME_INI_INDEX = 2;

    private ModTable tblModsList;
    private JPanel pnlRoot;
    private final XImage pnlModImage = new XImage(ImageUtils.WOTC_ICON.getImage());
    private final LauncherService launcherService;

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
    private JPanel pnlModInfo;
    private JLabel lblErrorsDetected;
    private FlatEditorPane epXcomGameIni;
    private FlatTabbedPane tpModInfo;
    private XScrollPane spXcomGameIni;
    private JLabel lblModsCount;
    private final MainFormMenuBar mainFormMenuBar;
    private final SwingService swingService;
    @Getter
    private List<Mod> mods = new ArrayList<>();
    private Mod selectedMod;


    public void init() {
        setTitle("XCOM Chaos Mod Launcher 1.0.0");
        this.setIconImage(ImageUtils.WOTC_ICON.getImage());
        this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                launcherService.exitApp();
            }
        });
        this.setContentPane(pnlRoot);
        setSize(1336, 768);
        setMinimumSize(new Dimension(320, 240));
        setLocationRelativeTo(null);
        this.setJMenuBar(mainFormMenuBar);
        pbSteamProgress.setVisible(false);
        tfModFilter.setLeadingIcon(ImageUtils.SEARCH_ICON);

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


        // Load INI content lazily when ini tab is selected
        tpModInfo.addChangeListener(e -> {
            if (tpModInfo.getSelectedIndex() == TAB_XCOM_GAME_INI_INDEX) {
                loadIniForSelectedMod();
            }
        });

        swingService.applyFullWindowState("MainForm", this);
        setVisible(true);
    }

    private ModService getModService() {
        return CDI.current().select(ModService.class).get();
    }

    public void setMods(List<Mod> parsedMods) {
        Mod currentSelectedMod = selectedMod;
        this.mods = parsedMods;
        tblModsList.setMods(parsedMods);
        selectModIfExist(currentSelectedMod);
        var okStatus = Set.of(ModStatus.OK);
        long errorsCount = parsedMods.stream().filter(v -> v.isActive() && !okStatus.equals(v.getStatuses())).count();
        lblErrorsDetected.setVisible(errorsCount > 0);
        lblErrorsDetected.setText("Errors: " + errorsCount);
        lblErrorsDetected.setForeground(Color.RED);
        updateModsCount();
    }

    public void onMainTableDataChange() {
        Mod currentSelectedMod = selectedMod;
        tblModsList.getModel().fireTableDataChanged();
        selectModIfExist(currentSelectedMod);
    }

    private void updateModsCount() {
        int active = 0;
        int inactive = 0;
        int total = 0;
        for (Mod m : mods) {
            if (m.isActive()) {
                active++;
            } else {
                inactive++;
            }
            total++;
        }
        lblModsCount.setText(active + "/" + total);
    }

    public void selectModIfExist(Mod mod) {
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
        pnlModInfo.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),
                "<html>Mod information" + (mod == null ? "" : String.format(" - <strong>%s (%s)</strong>", mod.getTitle(), mod.getId())) + "</html>"));
        if (mod == null) {
            pnlModImage.setImage((Image) null);
            epXcomMod.setText(null);
            epSteamDescription.setText(null);

            boolean wasSelected = tpModInfo.getSelectedIndex() == TAB_XCOM_GAME_INI_INDEX;
            tpModInfo.setEnabledAt(TAB_XCOM_GAME_INI_INDEX, false);
            if (wasSelected) {
                tpModInfo.setSelectedIndex(0);
            }
        } else {
            pnlModImage.setImage(mod.getModPreviewImgFile());
            epXcomMod.setText(mod.getXcomModFileContent());
            epSteamDescription.setText("<html>" + StringUtils.stripToEmpty(mod.getSteamMod().getDescription()) + "</html>");

            Path iniPath = Path.of(mod.getDirectory().getAbsolutePath(), "Config", "XComGame.ini");
            boolean exists = Files.exists(iniPath);
            if (exists) {
                tpModInfo.setEnabledAt(TAB_XCOM_GAME_INI_INDEX, true);
                if (tpModInfo.getSelectedIndex() == TAB_XCOM_GAME_INI_INDEX) {
                    loadIniForSelectedMod();
                }
            } else {
                tpModInfo.setEnabledAt(TAB_XCOM_GAME_INI_INDEX, false);
                boolean wasSelected = tpModInfo.getSelectedIndex() == TAB_XCOM_GAME_INI_INDEX;
                if (wasSelected) {
                    tpModInfo.setSelectedIndex(0);
                }
            }
        }

        SwingUtilities.invokeLater(() -> {
            spXcomModDesc.getVerticalScrollBar().setValue(0);
            spXcomModDesc.getHorizontalScrollBar().setValue(0);
            spSteamDescription.getVerticalScrollBar().setValue(0);
            spSteamDescription.getHorizontalScrollBar().setValue(0);
            spXcomGameIni.getVerticalScrollBar().setValue(0);
            spXcomGameIni.getHorizontalScrollBar().setValue(0);
        });
    }

    private void loadIniForSelectedMod() {
        Mod mod = selectedMod;
        if (mod == null) {
            epXcomGameIni.setText("");
            return;
        }
        Path iniPath = new File(mod.getDirectory() + "/Config/XComGame.ini").toPath();
        if (!Files.exists(iniPath)) {
            epXcomGameIni.setText("");
            return;
        }
        try {
            String content = Files.readString(iniPath, StandardCharsets.UTF_8);
            epXcomGameIni.setText(content);
        } catch (Exception e) {
            epXcomGameIni.setText("Failed to read XComGame.ini: " + e.getMessage());
        }
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
        scrollPane1.setViewportView(tblModsList);
        pnlModInfo = new JPanel();
        pnlModInfo.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPane2.setRightComponent(pnlModInfo);
        pnlModInfo.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Mod information", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final FlatSplitPane flatSplitPane1 = new FlatSplitPane();
        flatSplitPane1.setResizeWeight(0.0);
        pnlModInfo.add(flatSplitPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        flatSplitPane1.setRightComponent(panel2);
        tpModInfo = new FlatTabbedPane();
        panel2.add(tpModInfo, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        tpModInfo.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        tpModInfo.addTab("Steam description", panel3);
        spSteamDescription = new FlatScrollPane();
        spSteamDescription.setHorizontalScrollBarPolicy(30);
        spSteamDescription.setShowButtons(false);
        spSteamDescription.setSmoothScrolling(false);
        panel3.add(spSteamDescription, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        epSteamDescription = new FlatEditorPane();
        epSteamDescription.setContentType("text/html");
        epSteamDescription.setEditable(false);
        epSteamDescription.setText("<html>\r\n  <head>\r\n    \r\n  </head>\r\n  <body>\r\n  </body>\r\n</html>\r\n");
        spSteamDescription.setViewportView(epSteamDescription);
        btnSyncModSteamInfo = new JButton();
        btnSyncModSteamInfo.setText("Sync Steam mod info");
        panel3.add(btnSyncModSteamInfo, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spXcomModDesc = new JScrollPane();
        spXcomModDesc.setHorizontalScrollBarPolicy(30);
        tpModInfo.addTab("XComMod", spXcomModDesc);
        epXcomMod = new FlatEditorPane();
        epXcomMod.setEditable(false);
        epXcomMod.setMinimumSize(new Dimension(100, 23));
        epXcomMod.setText("Label");
        spXcomModDesc.setViewportView(epXcomMod);
        spXcomGameIni = new XScrollPane();
        tpModInfo.addTab("XComGame.ini", spXcomGameIni);
        epXcomGameIni = new FlatEditorPane();
        epXcomGameIni.setEditable(false);
        spXcomGameIni.setViewportView(epXcomGameIni);
        pnlModImage.setMinimumSize(new Dimension(50, 10));
        pnlModImage.setToolTipText("Some active mods have errors");
        flatSplitPane1.setLeftComponent(pnlModImage);
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPane1.setRightComponent(panel4);
        final FlatSplitPane flatSplitPane2 = new FlatSplitPane();
        flatSplitPane2.setOrientation(0);
        flatSplitPane2.setResizeWeight(0.33);
        flatSplitPane2.putClientProperty("html.disable", Boolean.FALSE);
        panel4.add(flatSplitPane2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        flatSplitPane2.setLeftComponent(panel5);
        panel5.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Declared dependencies", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane2 = new JScrollPane();
        panel5.add(scrollPane2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        tblDeclaredDependencies.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        scrollPane2.setViewportView(tblDeclaredDependencies);
        final FlatSplitPane flatSplitPane3 = new FlatSplitPane();
        flatSplitPane3.setOrientation(0);
        flatSplitPane3.setResizeWeight(0.33);
        flatSplitPane2.setRightComponent(flatSplitPane3);
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        flatSplitPane3.setLeftComponent(panel6);
        panel6.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Declared load orders", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane3 = new JScrollPane();
        panel6.add(scrollPane3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        tblDeclaredRunOrders.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        scrollPane3.setViewportView(tblDeclaredRunOrders);
        final FlatSplitPane flatSplitPane4 = new FlatSplitPane();
        flatSplitPane4.setOrientation(0);
        flatSplitPane4.setResizeWeight(0.5);
        flatSplitPane3.setRightComponent(flatSplitPane4);
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        flatSplitPane4.setLeftComponent(panel7);
        panel7.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Final dependencies", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane4 = new JScrollPane();
        panel7.add(scrollPane4, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        tblModDependencies.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        scrollPane4.setViewportView(tblModDependencies);
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        flatSplitPane4.setRightComponent(panel8);
        panel8.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Final load order", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JScrollPane scrollPane5 = new JScrollPane();
        panel8.add(scrollPane5, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        tblModRunOrders.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        scrollPane5.setViewportView(tblModRunOrders);
        cycleModIssuesPanel1.putClientProperty("html.disable", Boolean.FALSE);
        panel8.add(cycleModIssuesPanel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        pnlRoot.add(panel9, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        tfModFilter = new FlatTextField();
        tfModFilter.setPlaceholderText("Mod Filter");
        tfModFilter.setShowClearButton(true);
        tfModFilter.setToolTipText("Mod Filter");
        panel9.add(tfModFilter, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, new Dimension(150, -1), new Dimension(150, -1), new Dimension(150, -1), 0, false));
        pbSteamProgress = new JProgressBar();
        pbSteamProgress.setString("");
        pbSteamProgress.setStringPainted(true);
        pbSteamProgress.setValue(0);
        panel9.add(pbSteamProgress, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel9.add(panel10, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        lblErrorsDetected = new JLabel();
        lblErrorsDetected.setHorizontalAlignment(2);
        lblErrorsDetected.setHorizontalTextPosition(2);
        lblErrorsDetected.setText("");
        lblErrorsDetected.setToolTipText("Some active mods have errors and might work not properly.\nManual resolve needed.");
        lblErrorsDetected.setVisible(false);
        panel10.add(lblErrorsDetected, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel10.add(spacer1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        lblModsCount = new JLabel();
        lblModsCount.setText("0/0");
        lblModsCount.setToolTipText("Total mods count");
        lblModsCount.setVisible(true);
        panel10.add(lblModsCount, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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
