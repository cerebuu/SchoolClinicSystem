package clinic.app.ui.consultations;

import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.model.*;
import java.awt.BorderLayout;

public final class ConsultationsPanel extends BasePanel {
    public ConsultationsPanel(ApplicationContext app) {
        super("Consultations", clinic.app.Theme.SAMPLE_CAMPUS);
        PagedTablePanel<MedicalRecord> table = app.current().canAccess(User.RECORDS)
            ? new PagedTablePanel<>(new ConsultationsTableModel(), app::records) : null;
        if (table != null) add(table, BorderLayout.CENTER);
        else add(new EmptyState("No consultation access",
            "Record a consultation using an authorized clinic account."), BorderLayout.CENTER);
        if (app.current().canAccess(User.RECORD_TRANSACTION)) {
            action("Record visit", true, () -> NewConsultationForm.open(this, app, () -> {
                if (table != null) table.reload();
            }));
        }
    }
}
