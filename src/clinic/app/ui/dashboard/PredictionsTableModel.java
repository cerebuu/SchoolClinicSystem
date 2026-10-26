package clinic.app.ui.dashboard;
import clinic.app.ui.common.PagedTableModel;
public final class PredictionsTableModel extends PagedTableModel<MedicinePrediction> {
    public PredictionsTableModel(){super("Medicine","Unexpired stock","Estimated days remaining");}
    protected Object value(MedicinePrediction p,int column){return switch(column){
        case 0->p.medicine();case 1->p.stock();default->Double.isInfinite(p.days())?"No usage history":String.format(java.util.Locale.ROOT,"%.1f",p.days());};}
}
