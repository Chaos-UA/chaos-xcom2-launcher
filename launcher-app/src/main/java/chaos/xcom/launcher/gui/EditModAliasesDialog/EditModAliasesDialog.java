package chaos.xcom.launcher.gui.EditModAliasesDialog;

import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.GlazedLists;
import chaos.xcom.launcher.gui.component.XScrollPane;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.steam.SteamMod;
import chaos.xcom.launcher.swing.SwingService;
import chaos.xcom.launcher.util.AutoCompleteSupportUtils;
import com.formdev.flatlaf.extras.components.FlatComboBox;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class EditModAliasesDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JScrollPane scrollPaneRules;
    private JButton btnAddAlias;
    private final List<ModAlias> modsAliases;
    private final Collection<String> modIds;
    private final TreeSet<Long> steamModIds;
    private final Map<String, Mod> mods;
    private final PanelRules panelRules;
    private final JPanel pnlRules;
    private final ModService modService = ModService.get();

    private List<ModAlias> buildModsAliases(Mod selectedMod, Long selectedSteamModId, Map<String, Mod> mods) {
        List<ModAlias> result = new ArrayList<>(mods.size() + 1);

        for (Mod mod : mods.values()) {
            for (Long aliasModId : mod.getSteamAliasModIds()) {
                result.add(new ModAlias(mod.getId(), aliasModId));
            }
        }

        // if selectedMod not null then selected mod should be first
        // else if selectedSteamModId not null then selectedSteamModId should be first
        result.sort(new Comparator<ModAlias>() {
            @Override
            public int compare(ModAlias o1, ModAlias o2) {
                if (selectedMod != null) {
                    boolean o1Match = Objects.equals(o1.modId, selectedMod.getId());
                    boolean o2Match = Objects.equals(o2.modId, selectedMod.getId());
                    if (o1Match != o2Match) {
                        return o1Match ? -1 : 1;
                    }
                } else if (selectedSteamModId != null) {
                    boolean o1Match = Objects.equals(o1.aliasSteamModId, selectedSteamModId);
                    boolean o2Match = Objects.equals(o2.aliasSteamModId, selectedSteamModId);
                    if (o1Match != o2Match) {
                        return o1Match ? -1 : 1;
                    }
                }
                return 0;
            }
        });

        return result;
    }

    public EditModAliasesDialog(Mod selectedMod, Long selectedSteamModId, Map<String, Mod> mods) {
        String selectedModId = selectedMod == null ? null : selectedMod.getId();
        this.mods = mods;
        this.modIds = mods.keySet();
        this.steamModIds = new TreeSet<>(modService.getSteamMods().keySet());
        for (Mod mod : mods.values()) {
            for (SteamMod.SteamRequiredMod requiredMod : mod.getSteamMod().getRequiredSteamMods()) {
                steamModIds.add(requiredMod.getSteamModId());
            }
        }
        this.modsAliases = buildModsAliases(selectedMod, selectedSteamModId, mods);

        this.panelRules = new PanelRules();
        this.pnlRules = panelRules;
        $$$setupUI$$$();
        setContentPane(contentPane);

        setTitle("Edit custom user mod aliases");
        setModal(true);
        setMinimumSize(new Dimension(500, 300));
        getRootPane().setDefaultButton(buttonOK);
        scrollPaneRules.getVerticalScrollBar().setUnitIncrement(XScrollPane.UNIT_INCREMENT);

        buttonOK.addActionListener(e -> onOK());

        buttonCancel.addActionListener(e -> onCancel());

        btnAddAlias.addActionListener(e -> {
            ModAlias rule = new ModAlias(selectedModId, selectedSteamModId);
            modsAliases.add(0, rule);
            panelRules.rebuildComponents();
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        panelRules.rebuildComponents();
        pack();
        setLocationRelativeTo(SwingService.getLastActiveWindowBounds());
        SwingService.get().applyFullWindowState("EditModAliasesDialog", this);
        setVisible(true);
    }

    private void onOK() {
        // add your code here
        HashMap<String, TreeSet<Long>> modAliases = new HashMap<>();
        for (ModAlias modAlias : modsAliases) {
            modAliases.computeIfAbsent(modAlias.modId, k -> new TreeSet<>())
                    .add(modAlias.getAliasSteamModId());
        }
        modService.applyModAliases(modAliases);
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
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
        contentPane = new JPanel();
        contentPane.setLayout(new GridLayoutManager(2, 1, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("OK");
        panel2.add(buttonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnAddAlias = new JButton();
        btnAddAlias.setText("Add Alias");
        btnAddAlias.setToolTipText("Add Steam ID alias. Some mods have live version and beta versions which can be used instead as alternative.\nFor example some mods require as dependency LWOTC, \nyou might want to use LWOTC_Experimental mod instead \nand here it is possible to specify Alias to map LWOTC to another steam ID (LWOTC_Experimental) \nto let mod manager to resolve such mod under another Steam mod ID");
        panel1.add(btnAddAlias, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        scrollPaneRules = new JScrollPane();
        contentPane.add(scrollPaneRules, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        scrollPaneRules.setViewportView(pnlRules);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }

    class PanelRules extends JPanel {

        public PanelRules() {
            rebuildComponents();
        }

        void rebuildComponents() {
            this.removeAll();
            int colCount = 4;

            setLayout(new GridLayoutManager(modsAliases.size() + 1, colCount, new Insets(0, 0, 0, 0), -1, -1));

            int row = 0;
            for (; row < modsAliases.size(); row++) {
                ModAlias rule = modsAliases.get(row);
                int colIndex = 0;
                FlatComboBox<String> cbMod = new FlatComboBox<>();
                cbMod.setEditable(false);
                AutoCompleteSupportUtils.install(cbMod, modIds);

                cbMod.setSelectedItem(rule.getModId());
                cbMod.addActionListener(e -> {
                    rule.setModId((String) cbMod.getSelectedItem());
                });
                //AutoCompleteDecorator.decorate(cbMod);
                this.add(cbMod, new GridConstraints(row, colIndex++, 1, 1,
                        GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        null, null, null, 0, false));

                // hardcode alias to steam as we have only 1 type
                FlatComboBox<AliasType> cbRule = new FlatComboBox<>();
                for (AliasType ruleType : AliasType.values()) {
                    cbRule.addItem(ruleType);
                }
                cbRule.setSelectedItem(AliasType.STEAM_MOD_ID_ALIAS);
                this.add(cbRule, new GridConstraints(row, colIndex++, 1, 1,
                        GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        null, null, null, 0, false));

                EventList<ModAliasItem> items = GlazedLists.eventListOf();
                for (Long modId : steamModIds) {
                    String displayValue = modId.toString();

                    SteamMod mod = modService.findSteamMod(modId).orElse(null);
                    if (mod != null && StringUtils.isNotBlank(mod.getSteamModName())) {
                        displayValue += " " + mod.getSteamModName();
                    }

                    items.add(new ModAliasItem(modId, displayValue));
                }

                FlatComboBox<ModAliasItem> cbTargetMod = new FlatComboBox<>();
                cbTargetMod.setEditable(false);
                cbTargetMod.setPrototypeDisplayValue(
                        new ModAliasItem(9999999999L, "MMMMMMMMMMMMMMMMM"));
                AutoCompleteSupportUtils.install(cbTargetMod, items);

                for (ModAliasItem item : items) {
                    if (Objects.equals(item.getSteamModId(), rule.getAliasSteamModId())) {
                        cbTargetMod.setSelectedItem(item);
                        break;
                    }
                }

                cbTargetMod.addActionListener(e -> {
                    Object selected = cbTargetMod.getSelectedItem();

                    if (selected instanceof ModAliasItem item) {
                        rule.setAliasSteamModId(item.getSteamModId());
                    }
                });

                this.add(cbTargetMod, new GridConstraints(row, colIndex++, 1, 1,
                        GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        null, null, null, 0, false));

                JButton btnDeleteRul = new JButton("Delete");
                btnDeleteRul.addActionListener(e -> {
                    modsAliases.remove(rule);
                    rebuildComponents();
                });
                this.add(btnDeleteRul, new GridConstraints(row, colIndex++, 1, 1,
                        GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                        GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                        null, null, null, 0, false));
            }

            Spacer spacer = new Spacer();
            this.add(spacer, new GridConstraints(row++, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
            revalidate();
            repaint();
        }

    }

    enum AliasType {
        STEAM_MOD_ID_ALIAS
    }

    @Data
    @AllArgsConstructor
    static class ModAlias {
        private String modId;
        private Long aliasSteamModId;
    }

    @Data
    @AllArgsConstructor
    static class ModAliasItem {
        private final Long steamModId;
        private final String displayValue;

        public String toString() {
            return displayValue;
        }
    }

}
