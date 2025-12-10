package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.TableColumn;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.gui.component.event.XMouseAdapter;
import chaos.xcom.launcher.gui.component.event.XTableModel;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.DeclarationSource;
import chaos.xcom.launcher.mod.dto.DependencyType;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.mod.dto.ModDeclaredDependency;
import chaos.xcom.launcher.steam.SteamMod;
import chaos.xcom.launcher.steam.SteamMod.SteamRequiredMod;
import chaos.xcom.launcher.steam.SteamService;
import chaos.xcom.launcher.util.ColorConstant;
import org.jdesktop.swingx.decorator.AbstractHighlighter;
import org.jdesktop.swingx.decorator.ComponentAdapter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class ModDeclaredDependenciesTable extends XTable {
    private static final int TARGET_MOD_ID_COLUMN_INDEX = 2;
    private ModDependenciesTableModel model;

    public ModDeclaredDependenciesTable() {
        this.model = new ModDependenciesTableModel();
        setSortable(false);
        model.apply(this);
        setMod(null);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int selectedRow = rowAtPoint(e.getPoint());
                if (selectedRow < 0) {
                    return;
                }

                ModDeclaredDependency row = model.getRows().get(selectedRow);
                if (row.getTargetMod() == null && row.getSteamRequiredMod().getSteamModId() != null) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });

        addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                int selectedRowIndex =  rowAtPoint(e.getPoint());
                if (selectedRowIndex < 0) {
                    return;
                }
                ModDeclaredDependency row = model.getRows().get(selectedRowIndex);
                if (row.getTargetMod() == null && row.getSteamRequiredMod().getSteamModId() != null) {
                    SteamService.openSteamModInBrowser(row.getSteamRequiredMod().getSteamModId());
                }
            }
        });

        this.addHighlighter(new AbstractHighlighter() {
            @Override
            protected Component doHighlight(Component component, ComponentAdapter adapter) {
                ModDeclaredDependency row = model.getRows().get(convertRowIndexToModel(adapter.row));
                if (row == null || row == null) {
                    return component;
                }
                if (row.isHasError()) {
                    component.setBackground(ColorConstant.ERROR.getColor());
                }
                if (row.getTargetMod() == null && adapter.column == TARGET_MOD_ID_COLUMN_INDEX) {
                    component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                }
//                int colIndex = convertColumnIndexToModel(adapter.column);
//                if (colIndex == MOD_ID_COLUMN_INDEX) {
//                    JCheckBox checkBox = (JCheckBox) component;
//                    checkBox.setHorizontalAlignment(SwingConstants.LEFT);
//                    checkBox.setSelected(getModService().isModActive(row.getModId()));
//                    checkBox.setText(row.getModId());
//                    if (!getModService().isModExist(row.getModId())) {
//                        checkBox.setEnabled(false);
//                        //checkBox.setForeground(ColorConstant.getLabelDisabledForegroundColor());
//                        //JLabel lbl = new JLabel("  " + row.getTargetModId());
//                        //component = lbl;
//                        component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
//                    }
//                } else if (colIndex == TARGET_MOD_ID_COLUMN_INDEX) {
//                    JCheckBox checkBox = (JCheckBox) component;
//                    checkBox.setHorizontalAlignment(SwingConstants.LEFT);
//                    checkBox.setSelected(getModService().isModActive(row.getTargetModId()));
//                    checkBox.setText(row.getTargetModId());
//                    if (!getModService().isModExist(row.getTargetModId())) {
//                        checkBox.setEnabled(false);
//                        //JLabel lbl = new JLabel("  " + row.getTargetModId());
//                        //component = lbl;
//                        component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
//                    }
//                }
//
//                if (row.getOverriddenByModId() != null) {
//                    component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
//                }
//                if (row.isHasError()) {
//                    component.setBackground(ColorConstant.ERROR.getColor());
//                }

                return component;
            }
        });
    }

    public void setMod(Mod mod) {
        model.setMod(mod);
        model.apply(this);
        packAll();
    }

    ModService getModService() {
        return ModService.get();
    }

    public class ModDependenciesTableModel extends XTableModel<ModDeclaredDependency> {

        public ModDependenciesTableModel() {
            super(createTableColumns());
        }

        private static TableColumn[] createTableColumns() {
            return new TableColumn[]{
                    new TableColumn<>("Mod ID", String.class, ModDeclaredDependency::getMod),
                    new TableColumn<>("Dependency", DependencyType.class, ModDeclaredDependency::getDependencyType),
                    new TableColumn<>("Target Mod ID", String.class, new Function<ModDeclaredDependency, String>() {
                        @Override
                        public String apply(ModDeclaredDependency declaredDependency) {
                            String targetMod = declaredDependency.getTargetMod();
                            if (targetMod == null) {
                                SteamRequiredMod steamRequiredMod = declaredDependency.getSteamRequiredMod();
                                return steamRequiredMod.getSteamModName() + " - [" + steamRequiredMod.getSteamModId() + "]";
                            }
                            return targetMod;
                        }
                    }),
                    new TableColumn<>("Source", DeclarationSource.class, ModDeclaredDependency::getSource)
            };
        }

        public void setMod(Mod mod) {
            if (mod == null) {
                rows = new ArrayList<>();
            } else {
                rows = mod.getDeclaredDependencies();
            }
        }
    }
}
