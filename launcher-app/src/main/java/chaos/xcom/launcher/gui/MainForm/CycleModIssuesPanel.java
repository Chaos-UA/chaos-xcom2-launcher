package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.util.ColorConstant;
import chaos.xcom.launcher.util.SortUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jdesktop.swingx.JXList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.stream.Collectors;

public class CycleModIssuesPanel extends JPanel {
    private final JXList cycleModIssuesList = new JXList();
    private Mod mod;

    public CycleModIssuesPanel() {
        this.setMod(null);
        this.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Cycle load order issues"));
        this.setLayout(new BorderLayout());
        this.add(cycleModIssuesList, BorderLayout.CENTER);
        cycleModIssuesList.setBackground(ColorConstant.ERROR);
        cycleModIssuesList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel();
            Mod.CycleGroup row = (Mod.CycleGroup) value;
            String line = row.getMods().stream().map(SortUtils.SortItem::getValue).collect(Collectors.joining(", "));
            label.setText(line);
            return label;
        });
        cycleModIssuesList.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = cycleModIssuesList.locationToIndex(e.getPoint());
                if (index > -1) {
                    Mod.CycleGroup cycleGroup = (Mod.CycleGroup) cycleModIssuesList.getModel().getElementAt(index);
                    StringBuilder sb = new StringBuilder();
                    sb.append("<html><table border='0' cellpadding='2' cellspacing='2'>");
                    for (SortUtils.SortItem<String> mod : cycleGroup.getMods()) {
                        for (String targetMod : mod.getBeforeValues()) {
                            sb.append("<tr>")
                                    .append("<td>").append(StringEscapeUtils.escapeHtml4(mod.getValue())).append("</td>")
                                    .append("<td>").append("BEFORE").append("</td>")
                                    .append("<td>").append(StringEscapeUtils.escapeHtml4(targetMod)).append("</td>")
                                    .append("</tr>");
                        }
                        for (String targetMod : mod.getAfterValues()) {
                            sb.append("<tr>")
                                    .append("<td>").append(StringEscapeUtils.escapeHtml4(mod.getValue())).append("</td>")
                                    .append("<td>").append("AFTER").append("</td>")
                                    .append("<td>").append(StringEscapeUtils.escapeHtml4(targetMod)).append("</td>")
                                    .append("</tr>");
                        }
                    }
                    sb.append("</table></html>");
                    cycleModIssuesList.setToolTipText(sb.toString());
                } else {
                    cycleModIssuesList.setToolTipText(null);
                }
            }
        });
    }

    public void setMod(Mod mod) {
        this.mod = mod;
        this.setVisible(mod != null && !mod.getCycleMods().isEmpty());
        updateIssuesList();
    }

    private void updateIssuesList() {
        DefaultListModel<Mod.CycleGroup> model = new DefaultListModel<>();
        if (mod != null) {
            for (Mod.CycleGroup cycleMods : mod.getCycleMods()) {
                model.addElement(cycleMods);
            }
        }
        cycleModIssuesList.setModel(model);
        cycleModIssuesList.setToolTipText(""); // enable tooltips
        revalidate();
        repaint();
    }
}
