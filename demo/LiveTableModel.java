import clinic.app.ui.common.PagedTableModel;
public final class LiveTableModel extends PagedTableModel<String> {
    public LiveTableModel(){super("ID");}
    protected Object value(String row,int column){return row;}
}
