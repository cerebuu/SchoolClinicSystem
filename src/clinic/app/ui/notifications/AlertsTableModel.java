package clinic.app.ui.notifications;
import clinic.app.ui.common.PagedTableModel;
import clinic.model.Alert;
import clinic.util.DateUtil;
public final class AlertsTableModel extends PagedTableModel<Alert> {
    public AlertsTableModel(){super("Alert","Message","Detected");}
    public Object key(int index){return row(index).getTrackingKey();}
    protected Object value(Alert a,int column){return switch(column){case 0->a.getType();case 1->a.getMessage();default->DateUtil.format(a.getCreatedAt());};}
    @Override public String toolTipAt(int row,int column) {
        return column==1?row(row).getMessage():null;
    }
}
