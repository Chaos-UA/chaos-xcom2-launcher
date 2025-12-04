package chaos.xcom.launcher.gui.MainForm;

import chaos.xcom.launcher.gui.component.TableColumn;
import chaos.xcom.launcher.gui.component.XTable;
import chaos.xcom.launcher.gui.component.event.XTableModel;
import chaos.xcom.launcher.mod.ModService;
import chaos.xcom.launcher.mod.dto.Mod;
import chaos.xcom.launcher.mod.dto.Mod.ModLoadOrderDeclaration;
import chaos.xcom.launcher.mod.dto.ModLoadOrder;
import chaos.xcom.launcher.util.ColorConstant;
import jakarta.enterprise.inject.spi.CDI;
import lombok.Data;
import org.jdesktop.swingx.decorator.AbstractHighlighter;
import org.jdesktop.swingx.decorator.ComponentAdapter;

import java.awt.*;
import java.util.ArrayList;

/**
 * Mod final load orders table.
 */
public class ModLoadOrdersTable extends XTable {
    private ModDeclaredRunOrdersModel model;
    private Mod mod;

    public ModLoadOrdersTable() {
        this.model = new ModDeclaredRunOrdersModel();
        setSortable(false);
        setMod(null);
        this.addHighlighter(new AbstractHighlighter() {
            @Override
            protected Component doHighlight(Component component, ComponentAdapter adapter) {
                ModRunOrderTableRow row = model.getRow(convertRowIndexToModel(adapter.row));
                if (mod == null || !mod.getId().equals(row.getModId())) {
                    return component;
                }

                ModService modService = getModService();
                if (row.getOverriddenByModId() != null) {
                    component.setForeground(ColorConstant.getLabelDisabledForegroundColor());
                } else if (ModLoadOrder.LOAD_AFTER_REQUIRED.name().equals(row.getRunOrderType())
                        && !modService.isModActive(row.getTargetModId())) {
                    component.setBackground(ColorConstant.MISSING_DEPENDENCY_MOD.getColor());
                }

                return component;
            }
        });
        model.apply(this);
    }

    public void setMod(Mod mod) {
        this.mod = mod;
        model.setMod(mod);
        model.apply(this);
        packAll();
    }

    ModService getModService() {
        return CDI.current().select(ModService.class).get();
    }

    @Data
    public static class ModRunOrderTableRow {
        private String modId;
        private String runOrderType;
        private String targetModId;
        private String declaredInModId;
        private String overriddenByModId;
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
                    new TableColumn<>("Overridden by Mod ID", String.class, ModRunOrderTableRow::getOverriddenByModId)
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
                    row.setOverriddenByModId(loadOrder.getOverriddenByMod());
                    row.setRunOrderType(loadOrder.getModLoadOrderGroup().name());
                    rows.add(row);
                }
                for (ModLoadOrderDeclaration loadOrder : mod.getLoadOrders()) {
                    ModRunOrderTableRow row = new ModRunOrderTableRow();
                    row.setModId(loadOrder.getMod());
                    row.setTargetModId(loadOrder.getTargetMod());
                    row.setDeclaredInModId(loadOrder.getDeclaredInMod());
                    row.setOverriddenByModId(loadOrder.getOverriddenByMod());
                    row.setRunOrderType(loadOrder.getModLoadOrder().name());
                    rows.add(row);
                }
            }
        }

        public ModRunOrderTableRow getRow(int row) {
            return getRows().get(row);
        }
    }
}
