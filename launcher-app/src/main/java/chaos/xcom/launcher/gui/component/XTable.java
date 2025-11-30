package chaos.xcom.launcher.gui.component;

import chaos.xcom.launcher.gui.component.event.XMouseAdapter;
import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.table.DefaultTableColumnModelExt;
import org.jdesktop.swingx.table.TableColumnExt;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.event.MouseEvent;
import java.util.List;

public class XTable extends JXTable {

    public XTable() {
        this(new DefaultTableModel());
    }

    public XTable(TableModel tableModel) {
        super(tableModel);
        setColumnControlVisible(true);   // Allow user to hide/show columns via a control button
        setRowHeight(22);   // Better row height

        // Enable sorting
        setAutoCreateRowSorter(true);

        getTableHeader().addMouseListener(new XMouseAdapter() {
            @Override
            public void popUpTrigger(MouseEvent e) {
                showColumnChooser(e);
            }
        });
    }

    public DefaultTableColumnModelExt getColumnModel() {
        return (DefaultTableColumnModelExt) columnModel;
    }

    public void showColumnChooser(MouseEvent e) {
        if (!isColumnControlVisible()) {
            return;
        }
        JPopupMenu popup = new JPopupMenu();
        DefaultTableColumnModelExt columnModel = getColumnModel();
        List<TableColumn> columns = columnModel.getColumns(true);

        for (int i = 0; i < columns.size(); i++) {
            TableColumnExt col = (TableColumnExt) columns.get(i);
            String name = col.getHeaderValue().toString();
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(name);
            item.setSelected(col.isVisible());
            item.addActionListener(ev -> col.setVisible(!col.isVisible()));
            popup.add(item);
        }

        popup.show(e.getComponent(), e.getX(), e.getY());
    }

}
