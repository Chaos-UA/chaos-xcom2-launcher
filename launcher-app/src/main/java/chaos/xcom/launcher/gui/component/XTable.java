package chaos.xcom.launcher.gui.component;

import chaos.xcom.launcher.gui.component.event.XMouseAdapter;
import lombok.extern.slf4j.Slf4j;
import org.jdesktop.swingx.JXTable;
import org.jdesktop.swingx.decorator.ComponentAdapter;
import org.jdesktop.swingx.decorator.Highlighter;
import org.jdesktop.swingx.table.DefaultTableColumnModelExt;
import org.jdesktop.swingx.table.TableColumnExt;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.table.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

@Slf4j
public class XTable extends JXTable {

    public XTable() {
        this(new DefaultTableModel());
    }

    public XTable(TableModel tableModel) {
        super(tableModel);
        setRowSorter(new TableRowSorter<>());
        setColumnControlVisible(true);   // Allow user to hide/show columns via a control button
        //setRowHeight(22);   // Better row height
        setShowHorizontalLines(true);
        setShowVerticalLines(true);
        setIntercellSpacing(new Dimension(0, 1));

        // Enable sorting
        setAutoCreateRowSorter(true);

        getTableHeader().addMouseListener(new XMouseAdapter() {
            @Override
            public void popUpTrigger(MouseEvent e) {
                showColumnChooser(e);
            }
        });

        setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {

                Component component = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                setComponentTooltipByText(component);
                return component;
            }
        });

        getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected, boolean hasFocus,
                    int row, int column) {

                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);

                setComponentTooltipByText(c);
                return c;
            }
        });
    }

    public static Component setComponentTooltipByText(Component component) {
        if (component instanceof JLabel c) {
            c.setToolTipText(c.getText());
        } else if (component instanceof JCheckBox c) {
            c.setToolTipText(c.getText());
        }
        return component;
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

    @Override
    public void addHighlighter(Highlighter highlighter) {
        Highlighter wrapper = new Highlighter() {
            @Override
            public Component highlight(Component renderer, ComponentAdapter adapter) {
                try {  // fix issue with intellij idea ui designer plugin that crash it with NPE
                    return highlighter.highlight(renderer, adapter);
                } catch (Exception e) {
                    log.error("Failed to process highlighter", e);
                    return renderer;
                }
            }

            @Override
            public void addChangeListener(ChangeListener l) {
                highlighter.addChangeListener(l);
            }

            @Override
            public void removeChangeListener(ChangeListener l) {
                highlighter.removeChangeListener(l);
            }

            @Override
            public ChangeListener[] getChangeListeners() {
                return highlighter.getChangeListeners();
            }
        };
        super.addHighlighter(wrapper);
    }
}
