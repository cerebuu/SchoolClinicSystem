package clinic.app.ui.admin;
import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.service.AuditEntry;
import java.awt.BorderLayout;
public final class AuditLogPanel extends BasePanel {
    public AuditLogPanel(ApplicationContext app) {
        super("Audit log - filter by user ID, YYYY-MM-DD, or denied");
        add(new PagedTablePanel<AuditEntry>(new AuditTableModel(), app.admin::audit), BorderLayout.CENTER);
    }
}
