package clinic.app.ui.reports;
import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.*;
import clinic.app.ui.consultations.ConsultationsTableModel;
import clinic.model.MedicalRecord;
import java.awt.BorderLayout;
public final class ReportsPanel extends BasePanel {
    public ReportsPanel(ApplicationContext app) {
        super("Reports", clinic.app.Theme.SAMPLE_CAMPUS);
        PagedTablePanel<MedicalRecord> table=new PagedTablePanel<>(new ConsultationsTableModel(), app::records);
        add(table, BorderLayout.CENTER);
        action("Generate selected report", true, ()-> {
            MedicalRecord r=table.selected();
            if(r==null) {
                MessagePopup.warning(this, "Select a record.");
                return;
            }
            Actions.run(this, ()->app.clinic().generateReport(r), report->MessagePopup.info(this, report));
        });
    }
}
