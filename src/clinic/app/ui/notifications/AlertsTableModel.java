package clinic.app.ui.notifications;
import clinic.app.ui.common.PagedTableModel;
import clinic.model.Alert;
public final class AlertsTableModel extends PagedTableModel<Alert> {
    public AlertsTableModel(){super("Alert","Message","Detected");}
    public Object key(int index){return java.util.List.of(row(index).getType(),row(index).getMessage());}
    protected Object value(Alert a,int column){return switch(column){case 0->a.getType();case 1->a.getMessage();default->a.getCreatedAt();};}
}
