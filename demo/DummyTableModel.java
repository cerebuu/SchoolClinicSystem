import clinic.app.ui.common.PagedTableModel;
public final class DummyTableModel extends PagedTableModel<Integer> {
    public DummyTableModel() {
        super("ID", "Description");
    }
    protected Object value(Integer row, int column) {
        return column==0?row:"Dummy record "+row;
    }
}
