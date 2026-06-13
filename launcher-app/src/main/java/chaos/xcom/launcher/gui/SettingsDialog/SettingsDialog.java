package chaos.xcom.launcher.gui.SettingsDialog;

import chaos.xcom.launcher.db.property.DbProperties;
import chaos.xcom.launcher.gui.LookAndFeelService;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.steam.SteamService;
import chaos.xcom.launcher.swing.SwingService;
import chaos.xcom.launcher.util.XComUtils;
import com.formdev.flatlaf.extras.components.FlatSpinner;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.CDI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.internal.util.OsUtils;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;

@Dependent
@RequiredArgsConstructor
@Slf4j
public class SettingsDialog extends JDialog {

    private final DbProperties dbProps;
    private final LookAndFeelService lookAndFeelService;
    private final SettingsService settingsService;
    private final SwingService swingService;
    private final List<JCheckBox> cbArgs = new ArrayList<>();

    private JPanel contentPane;
    private JButton buttonClose;
    private JPanel pnlGameDirs;
    private JTextField tfGameExe;
    private JButton btnGameDir;
    private JButton btnAddModDir;
    private JButton btnRemoveModDir;
    private JList jlModDirs;
    private JTextField tfGameLaunchArgs;
    private JComboBox<String> cbGuiSkins;
    private JCheckBox cbArgReview;
    private JCheckBox cbArgNoRedScreens;
    private JCheckBox cbArgAllowConsole;
    private JCheckBox cbArgNoStartupMovies;
    private JCheckBox cbArgLog;
    private JCheckBox cbArgAutoDebug;
    private JCheckBox cbArgNoSeekFreeLoading;
    private JCheckBox cbArgRegenerateinis;
    private JScrollPane contentScrollPane;
    private JCheckBox cbExitOnGameLaunch;
    private JTextField tfUserGameDir;
    private JButton btnChangeUserGameDir;
    private JTextField tfXComEngineIniFile;
    private JTextField tfXComModOptionsIniFile;
    private JLabel lblXComEngineError;
    private JLabel lblXCOmModOptionsIniError;
    private JLabel lblXComExe;
    private JCheckBox cbSyncMissingSteamModsOnModsReload;
    private JCheckBox cbGameLogEnabled;
    private FlatSpinner steamRequestDelaySpinner;

    public void openSettings() {
        this.setVisible(true);
    }

    private void onClose() {
        // add your code here if necessary
        dispose();
        SwingUtilities.invokeLater(() -> {
            getModService().reloadModsFromDirs();
        });
    }

    private ModService getModService() {
        return CDI.current().select(ModService.class).get();
    }

    @PostConstruct
    public void init() {
        setTitle("Settings");
        setContentPane(contentScrollPane);
        setSize(1366, 768);
        setMinimumSize(getPreferredSize());
        setModal(true);

        getRootPane().setDefaultButton(buttonClose);
        setLocationRelativeTo(SwingService.getLastActiveWindowBounds());

        cbArgs.add(cbArgReview);
        cbArgs.add(cbArgNoRedScreens);
        cbArgs.add(cbArgAllowConsole);
        cbArgs.add(cbArgNoStartupMovies);
        cbArgs.add(cbArgLog);
        cbArgs.add(cbArgAutoDebug);
        cbArgs.add(cbArgNoSeekFreeLoading);
        cbArgs.add(cbArgRegenerateinis);

        buttonClose.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onClose();
            }
        });

        reloadGameExe();
        btnGameDir.addActionListener((e) -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (OsUtils.isWindows()) {
                chooser.setFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        // allow directories so user can navigate
                        if (f.isDirectory()) return true;

                        // accept only .exe (case-insensitive)
                        return f.getName().toLowerCase().endsWith(".exe");
                    }

                    @Override
                    public String getDescription() {
                        return "XCOM WOTC executable file (*.exe)";
                    }
                });
            }
            chooser.setDialogTitle("Select XCOM executable file (usually XCom2-WarOfTheChosen/Binaries/Win64/XCom2.exe)");
            String exeFile = dbProps.gameExe.optional().orElse(null);
            if (exeFile == null) {
                chooser.setCurrentDirectory(SteamService.findXcomGameExeDirectory().orElse(null));
            } else {
                chooser.setCurrentDirectory(new File(exeFile).getParentFile());
            }
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedDir = chooser.getSelectedFile();
                String gameDir = selectedDir.getAbsolutePath();
                settingsService.updateGameExe(gameDir);
                reloadGameExe();
                reloadModDirs();
                reloadUserGameDir();
                log.info("Selected game exe: {}", gameDir);
            }
        });

        reloadUserGameDir();
        btnChangeUserGameDir.addActionListener((e) -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Select XCOM user directory. Usually in {user}/Documents/My Games/XCOM2 War of the Chosen");
            chooser.setCurrentDirectory(new File(dbProps.userGameConfigDir.get()));
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedDir = chooser.getSelectedFile();
                String gameDir = selectedDir.getAbsolutePath();
                dbProps.userGameConfigDir.set(gameDir);
                reloadUserGameDir();
                log.info("Selected user game dir: {}", gameDir);
            }
        });

        reloadModDirs();
        btnAddModDir.addActionListener((e) -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Select XCOM mod directory");
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedDir = chooser.getSelectedFile();
                String dir = selectedDir.getAbsolutePath();
                TreeSet<String> uniqueDirs = new TreeSet<>(dbProps.modDirsForSearch.get());
                uniqueDirs.add(dir);
                dbProps.modDirsForSearch.set(new ArrayList<>(uniqueDirs));
                log.info("Selected mod dir: {}", dir);
                reloadModDirs();
            }
        });
        btnRemoveModDir.addActionListener((e) -> {
            String modDir = (String) jlModDirs.getSelectedValue();
            if (modDir != null) {
                ArrayList<String> modDirs = new ArrayList<>(dbProps.modDirsForSearch.get());
                modDirs.remove(modDir);
                dbProps.modDirsForSearch.set(new ArrayList<>(modDirs));
                log.info("Removed mod dir: {}", modDir);
                reloadModDirs();
            }
        });
        jlModDirs.addListSelectionListener(e -> updateBtnRemoveModDir());

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onClose();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        for (String lookAndFeel : lookAndFeelService.getLookAndFeels()) {
            cbGuiSkins.addItem(lookAndFeel);
        }
        cbGuiSkins.setSelectedItem(lookAndFeelService.getCurrentLookAndFeel());
        cbGuiSkins.addActionListener(e -> {
            lookAndFeelService.applyLookAndFeel(cbGuiSkins.getSelectedItem().toString());
            SwingUtilities.invokeLater(() -> SwingUtilities.updateComponentTreeUI(this));
        });

        reloadArgs();
        for (JCheckBox cbArg : cbArgs) {
            cbArg.addActionListener(e -> {
                if (cbArg.isSelected()) {
                    dbProps.gameLaunchArgs.addUnique(cbArg.getText());
                } else {
                    dbProps.gameLaunchArgs.remove(cbArg.getText());
                }
                reloadArgs();
            });
        }
        tfGameLaunchArgs.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String argsStr = tfGameLaunchArgs.getText();
                if (argsStr != null) {
                    String[] args = argsStr.split(" ", -1);
                    TreeSet<String> uniqueArgs = new TreeSet<>();
                    for (String arg : args) {
                        arg = arg.trim();
                        if (!arg.isEmpty() && arg.length() != 1) {
                            uniqueArgs.add(arg.trim());
                        }
                    }
                    dbProps.gameLaunchArgs.set(new ArrayList<>(uniqueArgs));
                }
                reloadArgs();
            }
        });

        reloadCbExitOnGameLaunch();
        cbExitOnGameLaunch.addActionListener(e -> {
            dbProps.exitOnGameLaunch.set(cbExitOnGameLaunch.isSelected());
            reloadCbExitOnGameLaunch();
        });

        reloadCbSyncMissingSteamModsOnReload();
        cbSyncMissingSteamModsOnModsReload.addActionListener(e -> {
            dbProps.syncMissingSteamModsOnReload.set(cbSyncMissingSteamModsOnModsReload.isSelected());
            reloadCbSyncMissingSteamModsOnReload();
        });

        reloadGameLogEnabled();
        cbGameLogEnabled.addActionListener(e -> {
            dbProps.gameLogEnabled.set(cbGameLogEnabled.isSelected());
            reloadGameLogEnabled();
        });


        SpinnerNumberModel steamDelayModel = new SpinnerNumberModel(dbProps.steamRequestDelaySec.get().intValue(), 0, 600, 1);
        steamRequestDelaySpinner.setModel(steamDelayModel);
        // listen for changes and persist
        steamRequestDelaySpinner.addChangeListener(e -> {
            int val = (Integer) steamRequestDelaySpinner.getValue();
            if (val < 0) {
                val = 0;
                steamRequestDelaySpinner.setValue(0);
            }
            dbProps.steamRequestDelaySec.set(val);
            reloadSteamRequestDelay();
        });
        reloadSteamRequestDelay();

        swingService.applyFullWindowState("SettingsDialog", this);
    }

    private void reloadGameLogEnabled() {
        cbGameLogEnabled.setSelected(dbProps.gameLogEnabled.isTrue());
    }

    private void reloadSteamRequestDelay() {
        steamRequestDelaySpinner.setValue(dbProps.steamRequestDelaySec.get());
    }

    private void reloadGameExe() {
        String gameExe = dbProps.gameExe.optional().orElse(null);
        tfGameExe.setText(gameExe);
        lblXComExe.setVisible(gameExe == null || !new File(gameExe).isFile() || !new File(gameExe).canExecute());
    }

    private void reloadUserGameDir() {
        tfUserGameDir.setText(dbProps.userGameConfigDir.get());

        tfXComEngineIniFile.setText(dbProps.getXComEngineIniFile().getAbsolutePath());
        lblXComEngineError.setVisible(!dbProps.getXComEngineIniFile().exists());

        tfXComModOptionsIniFile.setText(dbProps.getXComModOptionsIniFile().getAbsolutePath());
        lblXCOmModOptionsIniError.setVisible(!dbProps.getXComEngineIniFile().exists());
    }

    private void reloadCbExitOnGameLaunch() {
        cbExitOnGameLaunch.setSelected(dbProps.exitOnGameLaunch.get());
    }

    private void reloadCbSyncMissingSteamModsOnReload() {
        cbSyncMissingSteamModsOnModsReload.setSelected(dbProps.syncMissingSteamModsOnReload.get());
    }

    private void reloadArgs() {
        Set<String> selectedArgs = new LinkedHashSet<>(dbProps.gameLaunchArgs.get());
        for (JCheckBox cbArg : cbArgs) {
            cbArg.setSelected(selectedArgs.contains(cbArg.getText()));
        }

        StringBuilder args = new StringBuilder();
        for (String arg : selectedArgs) {
            args.append(arg).append(" ");
        }
        tfGameLaunchArgs.setText(args.toString());
    }

    private void reloadModDirs() {
        DefaultListModel<String> model = new DefaultListModel<>();

        jlModDirs.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {

                JLabel c = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                String path = (String) value;
                File dir = new File(path);
                if (!dir.exists()) {
                    c.setText(path + " (not exists)");
                    c.setForeground(Color.RED);
                } else if (!dir.isDirectory()) {
                    c.setText(" (not a directory)");
                    c.setForeground(Color.RED);
                } else if (!dir.canRead()) {
                    c.setText(path + " (can't read)");
                    c.setForeground(Color.RED);
                } else if (XComUtils.isXcomWorkshopFolder(dir)) {
                    c.setText(path + " (steam mods)");
                }
                return c;
            }
        });

        model.addAll(dbProps.modDirsForSearch.get());

        jlModDirs.setModel(model);
        updateBtnRemoveModDir();
    }

    private void updateBtnRemoveModDir() {
        btnRemoveModDir.setEnabled(jlModDirs.getSelectedIndex() != -1);
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
        contentScrollPane = new JScrollPane();
        contentPane = new JPanel();
        contentPane.setLayout(new GridLayoutManager(6, 1, new Insets(10, 10, 10, 10), -1, -1));
        contentScrollPane.setViewportView(contentPane);
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonClose = new JButton();
        buttonClose.setText("Close");
        panel2.add(buttonClose, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel1.add(spacer2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        pnlGameDirs = new JPanel();
        pnlGameDirs.setLayout(new GridLayoutManager(8, 3, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(pnlGameDirs, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        pnlGameDirs.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Directories", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label1 = new JLabel();
        label1.setText("Mod directories");
        pnlGameDirs.add(label1, new GridConstraints(7, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        pnlGameDirs.add(panel3, new GridConstraints(7, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        btnAddModDir = new JButton();
        btnAddModDir.setText("Add");
        panel3.add(btnAddModDir, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnRemoveModDir = new JButton();
        btnRemoveModDir.setText("Remove");
        panel3.add(btnRemoveModDir, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        jlModDirs = new JList();
        pnlGameDirs.add(jlModDirs, new GridConstraints(7, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, 50), null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("User game directory");
        pnlGameDirs.add(label2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tfUserGameDir = new JTextField();
        tfUserGameDir.setEditable(false);
        pnlGameDirs.add(tfUserGameDir, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        btnChangeUserGameDir = new JButton();
        btnChangeUserGameDir.setText("Change");
        pnlGameDirs.add(btnChangeUserGameDir, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("XComEngine.ini");
        pnlGameDirs.add(label3, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("XComModOptions.ini");
        pnlGameDirs.add(label4, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tfXComEngineIniFile = new JTextField();
        tfXComEngineIniFile.setEditable(false);
        pnlGameDirs.add(tfXComEngineIniFile, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        tfXComModOptionsIniFile = new JTextField();
        tfXComModOptionsIniFile.setEditable(false);
        pnlGameDirs.add(tfXComModOptionsIniFile, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        lblXComEngineError = new JLabel();
        lblXComEngineError.setForeground(new Color(-3790808));
        lblXComEngineError.setText("XComEngine.ini not exists. Check path and you must start XCOM 2 once to generate the file");
        pnlGameDirs.add(lblXComEngineError, new GridConstraints(4, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lblXCOmModOptionsIniError = new JLabel();
        lblXCOmModOptionsIniError.setBackground(new Color(-13947600));
        lblXCOmModOptionsIniError.setForeground(new Color(-3790808));
        lblXCOmModOptionsIniError.setText("XComModOptions.ini not exists. Check path and you must start XCOM 2 once to generate the file");
        pnlGameDirs.add(lblXCOmModOptionsIniError, new GridConstraints(6, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("XCom2 (WOTC exe)");
        pnlGameDirs.add(label5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnGameDir = new JButton();
        btnGameDir.setText("Change");
        pnlGameDirs.add(btnGameDir, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lblXComExe = new JLabel();
        lblXComExe.setForeground(new Color(-3790808));
        lblXComExe.setText("File not exist or not executable, please check file location");
        pnlGameDirs.add(lblXComExe, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tfGameExe = new JTextField();
        tfGameExe.setEditable(false);
        tfGameExe.setName("");
        pnlGameDirs.add(tfGameExe, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel4.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Game launch options", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label6 = new JLabel();
        label6.setText("Launch arguments");
        panel4.add(label6, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tfGameLaunchArgs = new JTextField();
        panel4.add(tfGameLaunchArgs, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(2, 4, new Insets(0, 0, 0, 0), -1, -1));
        panel4.add(panel5, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        cbArgLog = new JCheckBox();
        cbArgLog.setText("-log");
        cbArgLog.setToolTipText("Opens a separate console (or window) to display the game’s Launch.log in real time.\nUseful for debugging / checking logs.");
        panel5.add(cbArgLog, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cbArgNoRedScreens = new JCheckBox();
        cbArgNoRedScreens.setText("-noRedscreens");
        cbArgNoRedScreens.setToolTipText("Disables “red screens” – debugging tool overlays that show non-critical error messages");
        panel5.add(cbArgNoRedScreens, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cbArgNoStartupMovies = new JCheckBox();
        cbArgNoStartupMovies.setText("-nostartupmovies");
        cbArgNoStartupMovies.setToolTipText("Skips the intro / startup movies, which makes the game launch faster");
        panel5.add(cbArgNoStartupMovies, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cbArgRegenerateinis = new JCheckBox();
        cbArgRegenerateinis.setText("-regenerateinis");
        cbArgRegenerateinis.setToolTipText("Regenerates your user config .ini files on game start, effectively wiping your config \nfolder and creating fresh ones. Useful for troubleshooting, especially with mods");
        panel5.add(cbArgRegenerateinis, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cbArgReview = new JCheckBox();
        cbArgReview.setText("-review");
        cbArgReview.setToolTipText("Starts the game in normal mode. Without it, the game may launch in a “developer” mode, \ngiving you access to debug features like quick tactical launch and debug strategy start");
        panel5.add(cbArgReview, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cbArgNoSeekFreeLoading = new JCheckBox();
        cbArgNoSeekFreeLoading.setText("-noSeekFreeLoading");
        cbArgNoSeekFreeLoading.setToolTipText("Related to Unreal debugging (unreal engine specific)");
        panel5.add(cbArgNoSeekFreeLoading, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cbArgAllowConsole = new JCheckBox();
        cbArgAllowConsole.setText("-allowconsole");
        cbArgAllowConsole.setToolTipText("Enables the in-game console. After this, you can open the console in-game (typically by pressing ~, or \\ / ', depending on keyboard)");
        panel5.add(cbArgAllowConsole, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cbArgAutoDebug = new JCheckBox();
        cbArgAutoDebug.setText("-autoDebug");
        cbArgAutoDebug.setToolTipText("Enables debug-related features (used for development / mod testing)");
        panel5.add(cbArgAutoDebug, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(5, 3, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel6, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel6.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "General", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JLabel label7 = new JLabel();
        label7.setText("GUI skin");
        panel6.add(label7, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel6.add(panel7, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        cbExitOnGameLaunch = new JCheckBox();
        cbExitOnGameLaunch.setText("Exit after starting XCOM 2");
        cbExitOnGameLaunch.setToolTipText("Exit after starting XCOM 2 to free RAM memory resources");
        panel7.add(cbExitOnGameLaunch, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cbGuiSkins = new JComboBox();
        cbGuiSkins.setToolTipText("After changing skin you may need to restart application to fully apply skin change");
        panel6.add(cbGuiSkins, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cbSyncMissingSteamModsOnModsReload = new JCheckBox();
        cbSyncMissingSteamModsOnModsReload.setText("Sync missing Steam info for mods on mods reload");
        panel6.add(cbSyncMissingSteamModsOnModsReload, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        cbGameLogEnabled = new JCheckBox();
        cbGameLogEnabled.setEnabled(true);
        cbGameLogEnabled.setText("xcom-game.log");
        cbGameLogEnabled.setToolTipText("If enabled and game started via launcher then output form process will be written to the file in launcher directory");
        cbGameLogEnabled.setVisible(false);
        panel6.add(cbGameLogEnabled, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel6.add(panel8, new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        steamRequestDelaySpinner = new FlatSpinner();
        steamRequestDelaySpinner.setToolTipText("Used only as fallback when Steam for some reason is not running \nto parse mods info and required mods from HTML page and is very slow.\nSteam may return 429 (too many requests) error if requests done too often via fallback.");
        panel8.add(steamRequestDelaySpinner, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label8 = new JLabel();
        label8.setText("Steam mod sync request delay in seconds");
        label8.setToolTipText("Used only as fallback when Steam for some reason is not running \nto parse mods info and required mods from HTML page and is very slow.\nSteam may return 429 (too many requests) error if requests done too often via fallback.");
        panel8.add(label8, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        contentPane.add(spacer3, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentScrollPane;
    }

}
