package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.TableColumn;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.gui.component.event.XMouseAdapter;
import chaos.xcom.launcher.gui.component.event.XTableModel;
import chaos.xcom.launcher.highlander.dto.HighlanderModConfig;
import chaos.xcom.launcher.highlander.dto.HighlanderModConfig.RunOrderDeclaration;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.DeclarationSource;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.mod.dto.ModDeclaredDependency;
import chaos.xcom.launcher.mod.dto.ModLoadOrder;
import chaos.xcom.launcher.mod.rule.UserRuleDeclaration;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

@Slf4j
public class ModDeclaredRunOrdersTable extends XTable {
    private ModDeclaredRunOrdersModel model;
    private Mod mod;

    public ModDeclaredRunOrdersTable() {
        this.model = new ModDeclaredRunOrdersModel();
        setSortable(false);
        setMod(null);
        model.apply(this);

        addMouseListener(new XMouseAdapter() {

            @Override
            public void popUpTrigger(MouseEvent e) {
                showMenu(e);
            }

            private void showMenu(MouseEvent e) {
                if (mod == null) {
                    return;
                }
                JPopupMenu menu = new JPopupMenu();
                JMenuItem editUserModRules = new JMenuItem("Edit mod rules");
                editUserModRules.addActionListener(ae -> {
                    ModService.get().openUserModRulesEditorDialog(mod);
                });
                menu.add(editUserModRules);

                int selectedRow = rowAtPoint(e.getPoint());
                if (selectedRow >= 0) {
                    ModRunOrderTableRow row = model.getRows().get(selectedRow);
                    if (row.getSource() == DeclarationSource.HIGHLANDER) {
                        File xcomGameIniFile = new File(mod.getDirectory() + "/Config/XComGame.ini");
                        if (xcomGameIniFile.isFile()) {
                            JMenuItem openXcomGameIniFile = new JMenuItem("Open Config/XComGame.ini of " + mod.getId());
                            openXcomGameIniFile.addActionListener(ae -> {
                                try {
                                    Desktop.getDesktop().open(xcomGameIniFile);
                                } catch (IOException ex) {
                                    log.error("Failed to open file: " + xcomGameIniFile, ex);
                                }
                            });
                            menu.add(openXcomGameIniFile);
                        }
                    }
                }

                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    public void setMod(Mod mod) {
        this.mod = mod;
        model.setMod(mod);
        model.apply(this);
        packAll();
    }

    @Data
    public static class ModRunOrderTableRow {
        private String modId;
        private String runOrderType;
        private String targetModId;
        private DeclarationSource source;
    }

    public static class ModDeclaredRunOrdersModel extends XTableModel<ModRunOrderTableRow> {

        public ModDeclaredRunOrdersModel() {
            super(createTableColumns());
        }

        private static TableColumn[] createTableColumns() {
            return new TableColumn[]{
                    new TableColumn<>("Mod ID", String.class, ModRunOrderTableRow::getModId),
                    new TableColumn<>("Run Order", String.class, ModRunOrderTableRow::getRunOrderType),
                    new TableColumn<>("Target Mod ID", String.class, ModRunOrderTableRow::getTargetModId),
                    new TableColumn<>("Source", DeclarationSource.class, ModRunOrderTableRow::getSource)
            };
        }

        public void setMod(Mod mod) {
            if (mod == null) {
                rows = new ArrayList<>();
            } else {
                rows = new ArrayList<>();
                for (UserRuleDeclaration userModRule : ModService.get().getUserModRulesByMod(mod.getId())) {
                    ModLoadOrder modLoadOrder = userModRule.getType().toLoadOrder();
                    if (modLoadOrder != null) {
                        ModRunOrderTableRow row = new ModRunOrderTableRow();
                        row.setModId(userModRule.getModId());
                        row.setRunOrderType(modLoadOrder.name());
                        row.setTargetModId(userModRule.getTargetModId());
                        row.setSource(DeclarationSource.USER);
                        rows.add(row);
                    }
                }
                for (HighlanderModConfig modConfig : mod.getHighlanderModsConfig().getModConfigs().values()) {
                    if (modConfig.getRunPriorityGroup() != null) {
                        ModRunOrderTableRow row = new ModRunOrderTableRow();
                        row.setModId(null);
                        row.setRunOrderType(modConfig.getRunPriorityGroup().name());
                        row.setTargetModId(modConfig.getMod());
                        row.setSource(DeclarationSource.HIGHLANDER);
                        rows.add(row);
                    }

                    for (RunOrderDeclaration runOrderDeclaration : modConfig.getRunOrderDeclarations()) {
                        ModRunOrderTableRow row = new ModRunOrderTableRow();
                        row.setModId(modConfig.getMod());
                        row.setRunOrderType(runOrderDeclaration.getModLoadOrder().name());
                        row.setTargetModId(runOrderDeclaration.getTargetMod());
                        row.setSource(DeclarationSource.HIGHLANDER);
                        rows.add(row);
                    }
                }
            }
        }
    }
}
