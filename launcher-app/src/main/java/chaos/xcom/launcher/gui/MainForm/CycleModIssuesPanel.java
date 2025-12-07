package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.util.ColorConstant;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;

public class CycleModIssuesPanel extends JPanel {
    private final JList<String> cycleModIssuesList = new JList<>();
    private Mod mod;

    public CycleModIssuesPanel() {
        this.setMod(null);
        this.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Cycle load order issues"));
        this.setLayout(new BorderLayout());
        this.add(cycleModIssuesList, BorderLayout.CENTER);
        cycleModIssuesList.setBackground(ColorConstant.MISSING_DEPENDENCY_MOD.getColor());
    }

    public void setMod(Mod mod) {
        this.mod = mod;
        this.setVisible(mod != null && !mod.getCycleMods().isEmpty());
        updateIssuesList();
    }

    private void updateIssuesList() {
        DefaultListModel<String> model = new DefaultListModel<>();
        if (mod != null) {
            for (List<Mod> cycleMods : mod.getCycleMods()) {
                String line = cycleMods.stream().map(Mod::getId).collect(Collectors.joining(", "));
                model.addElement(line);
            }
        }
        cycleModIssuesList.setModel(model);
        revalidate();
        repaint();
    }
}
