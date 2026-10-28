package clinic.app.ui.consultations;

import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.MessagePopup;
import clinic.model.*;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

public final class ConsultationsPanel extends BasePanel {
    public ConsultationsPanel(ApplicationContext app) {
        super("Consultations", app);
        PagedTablePanel<MedicalRecord> table = new PagedTablePanel<>(new ConsultationsTableModel(),
            app.current().canAccess(User.RECORDS) ? app::records : app::recentVisits,
            "Patient ID / concern");
        table.refreshOn(app.changes, clinic.app.DataTopic.VISITS);
        table.setRowActions(record->{
            List<RowAction> actions=new ArrayList<>();
            actions.add(new RowAction("View details",()->MessagePopup.infoWithCopies(this,record.toReport(),
                java.util.Map.of("Record ID",record.getRecordId(),"Patient ID",record.getPatientId()))));
            if(app.current().canAccess(User.RECORDS))
                actions.add(new RowAction("View patient history",()->Actions.runInSession(this,app.login,
                    ()->app.clinic().getVisitHistory(record.getPatientId()),visits->MessagePopup.info(this,
                        visits.isEmpty()?"No visit history found."
                            :visits.stream().map(MedicalRecord::toReport)
                                .collect(java.util.stream.Collectors.joining("\n\n"))))));
            return List.copyOf(actions);
        });
        add(table, BorderLayout.CENTER);
        if (app.current().canAccess(User.RECORD_TRANSACTION)) {
            action("+ New Consultation", true, () -> NewConsultationForm.open(this, app, () -> {

            }));
        }
    }
}
