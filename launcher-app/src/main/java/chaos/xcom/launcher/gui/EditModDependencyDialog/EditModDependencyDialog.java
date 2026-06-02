package chaos.xcom.launcher.gui.EditModDependencyDialog;

import chaos.xcom.launcher.gui.component.XScrollPane;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.mod.rule.UserRuleDeclaration;
import chaos.xcom.launcher.swing.SwingService;
import chaos.xcom.launcher.util.AutoCompleteSupportUtils;
import com.formdev.flatlaf.extras.components.FlatComboBox;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class EditModDependencyDialog extends JDialog {
    private Mod mod;
    private List<UserRuleDeclaration> userRules = new ArrayList<>();
    private Map<String, Mod> mods = Collections.emptyMap();
    private List<String> modIds = new ArrayList<>();

    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private final PanelRules panelRules = new PanelRules();
    private final JPanel pnlRules = panelRules;
    private JButton btnAddRule;
    private JScrollPane scrollPaneRules;


    public EditModDependencyDialog(Mod selectedMod, Map<String, Mod> mods,
                                   Map<String, List<UserRuleDeclaration>> userRulesByMod,
                                   Map<String, List<UserRuleDeclaration>> userRulesByTargetMod) {
        $$$setupUI$$$();
        setTitle("Edit custom user mod dependencies");
        setContentPane(contentPane);
        setModal(true);
        setMinimumSize(new Dimension(500, 300));
        getRootPane().setDefaultButton(buttonOK);
        scrollPaneRules.getVerticalScrollBar().setUnitIncrement(XScrollPane.UNIT_INCREMENT);

        this.mod = selectedMod;
        this.userRules.addAll(userRulesByMod.getOrDefault(selectedMod.getId(), Collections.emptyList()));
        this.userRules.addAll(userRulesByTargetMod.getOrDefault(selectedMod.getId(), Collections.emptyList()));
        for (UserRuleDeclaration userModRule : userRulesByMod.values().stream().flatMap(Collection::stream).toList()) {
            if (!Objects.equals(userModRule.getModId(), mod.getId())
                    && !Objects.equals(userModRule.getTargetModId(), mod.getId())) {
                // add other mods
                this.userRules.add(userModRule);
            }
        }
        for (int i = 0; i < userRules.size(); i++) {
            userRules.set(i, new UserRuleDeclaration(userRules.get(i))); // copy to not modify existing when canceled
        }
        this.mods = mods;
        this.modIds = new ArrayList<>(this.mods.keySet());
        this.modIds.sort(Comparator.comparing(String::toLowerCase));

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        btnAddRule.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                UserRuleDeclaration rule = new UserRuleDeclaration();
                rule.setModId(mod.getId());
                rule.setTargetModId(mod.getId());
                rule.setType(UserRuleDeclaration.RuleType.AFTER);
                userRules.add(0, rule);
                panelRules.rebuildComponents();
            }
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
        SwingService.get().applyFullWindowState("ModDependencyEditorDialog", this);
        setVisible(true);
    }

    private void onOK() {
        // add your code here
        ModService.get().applyUserModRules(this.userRules);
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
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

            setLayout(new GridLayoutManager(userRules.size() + 1, colCount, new Insets(0, 0, 0, 0), -1, -1));

            int row = 0;
            for (; row < userRules.size(); row++) {
                UserRuleDeclaration rule = userRules.get(row);
                int colIndex = 0;
                FlatComboBox<String> cbMod = new FlatComboBox<>();
                //for (String modId : modIds) {
                //    cbMod.addItem(modId);
                //}
                cbMod.setEditable(false);
                AutoCompleteSupportUtils.install(cbMod, modIds);
                cbMod.setSelectedItem(rule.getModId());
                cbMod.addActionListener(e -> {
                    rule.setModId((String) cbMod.getSelectedItem());
                });

                this.add(cbMod, new GridConstraints(row, colIndex++, 1, 1,
                        GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        null, null, null, 0, false));

                FlatComboBox<UserRuleDeclaration.RuleType> cbRule = new FlatComboBox<>();
                for (UserRuleDeclaration.RuleType ruleType : UserRuleDeclaration.RuleType.values()) {
                    cbRule.addItem(ruleType);
                }
                cbRule.setSelectedItem(rule.getType());
                cbRule.addActionListener(e -> {
                    rule.setType(UserRuleDeclaration.RuleType.valueOf(cbRule.getSelectedItem().toString()));
                });
                this.add(cbRule, new GridConstraints(row, colIndex++, 1, 1,
                        GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        null, null, null, 0, false));

                FlatComboBox<String> cbTargetMod = new FlatComboBox<>();
                //for (String modId : modIds) {
                //    cbTargetMod.addItem(modId);
                //}
                AutoCompleteSupportUtils.install(cbTargetMod, modIds);
                cbTargetMod.setSelectedItem(rule.getTargetModId());
                //AutoCompleteDecorator.decorate(cbTargetMod);
                cbTargetMod.addActionListener(e -> {
                    rule.setTargetModId((String) cbTargetMod.getSelectedItem());
                });
                this.add(cbTargetMod, new GridConstraints(row, colIndex++, 1, 1,
                        GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                        null, null, null, 0, false));

                JButton btnDeleteRul = new JButton("Delete");
                btnDeleteRul.addActionListener(e -> {
                    userRules.remove(rule);
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
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("OK");
        panel2.add(buttonOK, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel2.add(buttonCancel, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnAddRule = new JButton();
        btnAddRule.setText("Add rule");
        panel2.add(btnAddRule, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        scrollPaneRules = new JScrollPane();
        panel3.add(scrollPaneRules, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        scrollPaneRules.setViewportView(pnlRules);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
