package clinic.app.ui.reports;
import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.*;
import clinic.app.ui.consultations.ConsultationsTableModel;
import clinic.model.MedicalRecord;
import clinic.model.User;
import java.awt.BorderLayout;
public final class ReportsPanel extends BasePanel {
    public ReportsPanel(ApplicationContext app) {
        super("Reports", app);
        boolean reportOnly=app.current().canAccess(User.VIEW_REPORTS)
            && !app.current().canAccess(User.RECORDS);
        if(reportOnly) {
            add(new EmptyState("No reports are assigned to this account.",
                "Contact your administrator if you need access to a report."),BorderLayout.CENTER);
            return;
        }
        PagedTablePanel<MedicalRecord> table=new PagedTablePanel<>(new ConsultationsTableModel(), app::records);
        table.refreshOn(app.changes, clinic.app.DataTopic.VISITS);
        add(table, BorderLayout.CENTER);
        if(!reportOnly)action("Generate selected report", true, ()-> {
                MedicalRecord r=table.selected();
                if(r==null) {
                    MessagePopup.warning(this, "Select a record.");
                    return;
                }
                java.util.Map<String,String> ids=java.util.Map.of("Record ID",r.getRecordId(),"Patient ID",r.getPatientId());
                Actions.runInSession(this,app.login,()->app.clinic().generateReport(r),
                    report->MessagePopup.infoWithCopies(this,"Clinic report",report,ids,"Copy report",false));
            });
    }
}
