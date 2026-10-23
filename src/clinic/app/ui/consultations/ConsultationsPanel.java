package clinic.app.ui.consultations;

import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.model.*;
import java.awt.BorderLayout;

public final class ConsultationsPanel extends BasePanel {
    public ConsultationsPanel(ApplicationContext app) {
        super("Consultations", "Clinic records");
        PagedTablePanel<MedicalRecord> table = new PagedTablePanel<>(new ConsultationsTableModel(),
            app.current().canAccess(User.RECORDS) ? app::records : app::recentVisits,
            "Patient ID / reason");
        add(table, BorderLayout.CENTER);
        if (app.current().canAccess(User.RECORD_TRANSACTION)) {
            action("Record visit", true, () -> NewConsultationForm.open(this, app, () -> {
                if (table != null) table.reload();
            }));
        }
    }
}
