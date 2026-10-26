package clinic.app.ui.admin;
import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.service.AuditEntry;
import java.awt.BorderLayout;
public final class AuditLogPanel extends BasePanel {
    public AuditLogPanel(ApplicationContext app) {
        super("Audit log");
        add(new PagedTablePanel<AuditEntry>(new AuditTableModel(), app.admin::audit,
            "User ID / date / denied").refreshOn(app.changes,clinic.app.DataTopic.AUDIT), BorderLayout.CENTER);
    }
}
