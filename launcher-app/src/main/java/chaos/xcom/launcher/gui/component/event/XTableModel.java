package chaos.xcom.launcher.gui.component.event;

import chaos.xcom.launcher.gui.component.TableColumn;
import chaos.xcom.launcher.gui.component.XTable;
import lombok.Getter;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Getter
public class XTableModel<T> extends AbstractTableModel {

    protected final TableColumn[] columns;
    protected List<T> rows = new ArrayList<>();

    public XTableModel(TableColumn[] columns) {
        this.columns = columns;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column].name;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return columns[columnIndex].extractor.apply(rows.get(rowIndex));
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false; // Make cells non-editable
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columns[columnIndex].type;
    }

    public void apply(XTable tbl) {
        for (int i = 0; i < columns.length; i++) {
            TableColumn column = columns[i];
            Function<Object, String> renderAs = column.renderAs;
            if (renderAs != null) {
                tbl.getColumnModel().getColumn(i).setCellRenderer(new DefaultTableCellRenderer() {
                    @Override
                    protected void setValue(Object value) {
                        try {
                            setText(renderAs.apply(value));
                        } catch (Exception e) {
                            super.setValue(value);
                        }
                    }
                });
            }
        }
        tbl.setModel(this);
    }
}

