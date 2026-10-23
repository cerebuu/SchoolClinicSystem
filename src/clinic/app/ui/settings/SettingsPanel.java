package clinic.app.ui.settings;
import clinic.app.ApplicationContext;
import clinic.app.ui.common.BasePanel;
import javax.swing.JLabel;
import java.awt.BorderLayout;
public final class SettingsPanel extends BasePanel {
    public SettingsPanel(ApplicationContext app) {
        super("Session settings", app.current().canAccess(clinic.model.User.RECORD_TRANSACTION)
            ? "Clinic records" : null);
        clinic.app.ui.common.Card card = new clinic.app.ui.common.Card();
        card.setLayout(new BorderLayout(0, clinic.app.Theme.SPACE_4));
        card.add(new clinic.app.ui.common.PageHeader("Current session", "Account and storage information", null), BorderLayout.NORTH);
        javax.swing.JTextArea details = new javax.swing.JTextArea(
            "Name: " + app.current().getName() + "\nUser ID: " + app.current().getUserId()
            + "\nRole: " + app.current().getRole()
            + "\n\n" + app.storageDescription()
            + (app.isPersistent()?"\nDatabase: " + app.databaseLocation()
                + "\nBack up the complete data folder, including its key file, while the application is closed.":"")
            + "\n\nGuardian messages must be delivered outside this application. Mark sent only after delivery.");
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(clinic.app.Theme.font(clinic.app.Theme.TYPE_BODY, java.awt.Font.PLAIN));
        details.setForeground(clinic.app.Theme.INK);
        card.add(details, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);
    }
}
