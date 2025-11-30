package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.exception.InternalException;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.gui.component.event.XMouseAdapter;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.util.ColorConstant;
import org.apache.commons.lang3.StringUtils;
import org.jdesktop.swingx.decorator.AbstractHighlighter;
import org.jdesktop.swingx.decorator.ColorHighlighter;
import org.jdesktop.swingx.decorator.ComponentAdapter;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class ModTable extends XTable {

    public ModTable() {
        super(new ModTableModel(List.of()));
        addMouseListener(new XMouseAdapter() {

            @Override
            public void popUpTrigger(MouseEvent e) {
                showMenu(e);
            }

            private void showMenu(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int rowAtPoint = rowAtPoint(e.getPoint());
                    if (rowAtPoint >= 0) {
                        setRowSelectionInterval(rowAtPoint, rowAtPoint);
                        Mod mod = getSelectedMod();
                        if (mod == null) {
                            return;
                        }

                        JPopupMenu menu = new JPopupMenu();

                        JMenuItem openDir = new JMenuItem("Open mod directory");
                        openDir.addActionListener(ae -> {
                            try {
                                Desktop.getDesktop().open(mod.getDirectory());
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        });
                        menu.add(openDir);

                        if (StringUtils.isNoneBlank(mod.getPublishedFileId()) && !"0".equals(mod.getPublishedFileId())) {
                            JMenuItem openSteamMod = new JMenuItem("Open mod in Steam");
                            openSteamMod.addActionListener(ae -> {
                                try {
                                    Desktop.getDesktop().browse(new URI("https://steamcommunity.com/sharedfiles/filedetails/?id=" + mod.getPublishedFileId()));
                                } catch (Exception ex) {
                                    throw new InternalException().cause(ex);
                                }
                            });
                            menu.add(openSteamMod);
                        }

                        menu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });


        addHighlighter(new ColorHighlighter(
                (renderer, adapter) -> {
                    // 4. Decide whether to highlight
                    return adapter.row == 5;
                },
                ColorConstant.MISSING_DEPENDENCY_MOD.getColor(),
                Color.WHITE
        ));

    }

    /**
     * Fix LookAndFill change
     */
    @Override
    public void updateUI() {
        super.updateUI();
        if (getModel() != null) {
            getModel().apply(this);
        }
    }

    public ModTableModel getModel() {
        return (ModTableModel) super.getModel();
    }

    public Mod getSelectedMod() {
        int row = getSelectedRow();
        if (row != -1) {
            return getModel().getMod(convertRowIndexToModel(row));
        }
        return null;
    }
}
