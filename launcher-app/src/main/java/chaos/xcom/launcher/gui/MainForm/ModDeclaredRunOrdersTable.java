package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.TableColumn;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.gui.component.event.XTableModel;
import chaos.xcom.launcher.highlander.dto.HighlanderModConfig;
import chaos.xcom.launcher.highlander.dto.HighlanderModConfig.RunOrderDeclaration;
import chaos.xcom.launcher.mod.dto.DeclarationSource;
import chaos.xcom.launcher.mod.dto.Mod;
import lombok.Data;

import java.util.ArrayList;

public class ModDeclaredRunOrdersTable extends XTable {
    private ModDeclaredRunOrdersModel model;

    public ModDeclaredRunOrdersTable() {
        this.model = new ModDeclaredRunOrdersModel();
        setSortable(false);
        setMod(null);
        model.apply(this);
    }

    public void setMod(Mod mod) {
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

//                    for (String runBeforeModId : modConfig.getRunBeforeMods()) {
//                        ModRunOrderTableRow row = new ModRunOrderTableRow();
//                        row.setModId(modConfig.getMod());
//                        row.setRunOrderType("RunBefore");
//                        row.setTargetModId(runBeforeModId);
//                        rows.add(row);
//                    }
//
//                    for (String runAfterModId : modConfig.getRunAfterMods()) {
//                        ModRunOrderTableRow row = new ModRunOrderTableRow();
//                        row.setModId(modConfig.getMod());
//                        row.setRunOrderType("RunAfter");
//                        row.setTargetModId(runAfterModId);
//                        rows.add(row);
//                    }


                }
            }
        }
    }
}
