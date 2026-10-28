package clinic.app.ui.dashboard;
import clinic.app.*;
import clinic.app.ui.common.*;
import clinic.service.PageResult;
import java.awt.BorderLayout;
import javax.swing.*;
public final class MedicinePredictionsPanel extends JPanel {
    public MedicinePredictionsPanel(ApplicationContext app){
        super(new BorderLayout());setBackground(Theme.WHITE);
        add(new JLabel("Medicine Predictions"),BorderLayout.NORTH);
        PagedTablePanel<MedicinePrediction> table=new PagedTablePanel<>(new PredictionsTableModel(),(page,size,query)->{
            var medicines=app.dispensableMedicines(page,size,query);var clinic=app.clinic();
            var rows=medicines.rows().stream().map(m->new MedicinePrediction(m.getName(),m.getQuantity(),clinic.estimateDaysRemaining(m.getName()))).toList();
            return new PageResult<>(rows,medicines.total(),page,size);
        });
        table.setDashboardViewport(DashboardLayout.VISIBLE_ROWS,DashboardLayout.ROW_HEIGHT);
        table.refreshOn(app.changes,DataTopic.INVENTORY,DataTopic.TRANSACTIONS);
        add(table,BorderLayout.CENTER);
        DashboardLayout.sizeCard(this);
    }
}
