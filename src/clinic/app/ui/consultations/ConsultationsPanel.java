package clinic.app.ui.consultations;

import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.model.*;
import java.awt.BorderLayout;

public final class ConsultationsPanel extends BasePanel {
    public ConsultationsPanel(ApplicationContext app) {
        super("Consultations");
        PagedTablePanel<MedicalRecord> table = app.current().canAccess(User.RECORDS)
            ? new PagedTablePanel<>(new ConsultationsTableModel(), app::records) : null;
        if (table != null) add(table, BorderLayout.CENTER);
        else add(new javax.swing.JLabel("Record a consultation using the patient's ID."), BorderLayout.CENTER);
        if (app.current().canAccess(User.RECORD_TRANSACTION)) {
            action("Record visit", () -> NewConsultationForm.open(this, app, () -> {
                if (table != null) table.reload();
            }));
        }
    }
}
