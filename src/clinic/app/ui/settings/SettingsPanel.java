package clinic.app.ui.settings;
import clinic.app.ApplicationContext;
import clinic.app.ui.common.BasePanel;
import javax.swing.JLabel;
import java.awt.BorderLayout;
public final class SettingsPanel extends BasePanel {
    public SettingsPanel(ApplicationContext app) {
        super("Session settings", app.current().canAccess(clinic.model.User.RECORD_TRANSACTION)
            ? clinic.app.Theme.SAMPLE_CAMPUS : null);
        add(new JLabel("Signed in as "+app.current().getUserId()+" ("+app.current().getRole()+"). Data is held in memory for this application session."), BorderLayout.CENTER);
    }
}
