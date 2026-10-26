package clinic.app.ui.common;
import clinic.service.PageResult;
import javax.swing.table.AbstractTableModel;
import java.util.List;
public abstract class PagedTableModel<T> extends AbstractTableModel {
    private final String[] columns;
    private List<T> rows=List.of();
    protected PagedTableModel(String... columns) {
        this.columns=columns;
    }
    public int getRowCount() {
        return rows.size();
    }
    public int getColumnCount() {
        return columns.length;
    }
    public String getColumnName(int c) {
        return columns[c];
    }
    public T row(int index) {
        return rows.get(index);
    }
    public Object getValueAt(int r, int c) {
        return value(row(r), c);
    }
    protected abstract Object value(T row, int column);
    /** Stable row identity used to preserve selection across fresh detached page objects. */
    public Object key(int row){return getValueAt(row,0);}
    public void replace(PageResult<T> result) {
        rows=result.rows();
        fireTableDataChanged();
    }
}
