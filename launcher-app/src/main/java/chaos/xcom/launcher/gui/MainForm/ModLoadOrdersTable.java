package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.TableColumn;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.gui.component.event.XTableModel;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.mod.dto.Mod.ModLoadOrderDeclaration;
import lombok.Data;

import java.util.ArrayList;

/**
 * Mod final load orders table.
 */
public class ModLoadOrdersTable extends XTable {
    private ModDeclaredRunOrdersModel model;

    public ModLoadOrdersTable() {
        this.model = new ModDeclaredRunOrdersModel();
        setMod(null);
        model.apply(this);
    }

    public void setMod(Mod mod) {
        model.setMod(mod);
        model.apply(this);
    }

    @Data
    public static class ModRunOrderTableRow {
        private String modId;
        private String runOrderType;
        private String targetModId;
        private String declaredInModId;
        private String suppressedByModId;
        private Boolean active;
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
                    new TableColumn<>("Declared in Mod ID", String.class, ModRunOrderTableRow::getDeclaredInModId),
                    new TableColumn<>("Suppressed by Mod ID", String.class, ModRunOrderTableRow::getSuppressedByModId),
                    new TableColumn<>("Active", Boolean.class, ModRunOrderTableRow::getActive)
            };
        }

        public void setMod(Mod mod) {
            if (mod == null) {
                rows = new ArrayList<>();
            } else {
                rows = new ArrayList<>();
                for (Mod.ModLoadOrderGroupDeclaration loadOrder : mod.getLoadOrderGroups()) {
                    ModRunOrderTableRow row = new ModRunOrderTableRow();
                    row.setTargetModId(loadOrder.getTargetMod());
                    row.setDeclaredInModId(loadOrder.getDeclaredInMod());
                    row.setSuppressedByModId(loadOrder.getOverriddenByMod());
                    row.setActive(false);
                    row.setRunOrderType(loadOrder.getModLoadOrderGroup().name());
                    rows.add(row);
                }
                for (ModLoadOrderDeclaration loadOrder : mod.getLoadOrders()) {
                    ModRunOrderTableRow row = new ModRunOrderTableRow();
                    row.setModId(loadOrder.getMod());
                    row.setTargetModId(loadOrder.getTargetMod());
                    row.setDeclaredInModId(loadOrder.getDeclaredInMod());
                    row.setSuppressedByModId(loadOrder.getOverriddenByMod());
                    row.setActive(false);
                    row.setRunOrderType(loadOrder.getModLoadOrder().name());
                    rows.add(row);
                }
            }
        }
    }
}
