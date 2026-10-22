package clinic.app.ui.auth;
import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.*;
import java.awt.*;
import java.util.*;
import javax.swing.*;
public final class LoginPanel extends JPanel {
    private final JTextField id=new JTextField(24);
    private final JPasswordField password=new JPasswordField(24);
    private final JButton submit=Theme.button("Sign in", true);
    private int attempts;
    public LoginPanel(ApplicationContext app, Runnable authenticated) {
        super(new GridBagLayout());
        setBackground(Theme.BRAND_TINT);
        JPanel form=new JPanel(new GridLayout(0, 1, Theme.GAP, Theme.GAP));
        form.setBorder(Theme.padding());
        form.add(new JLabel("UIC School Clinic Management System"));
        form.add(new JLabel("Sign in"));
        JLabel idLabel=new JLabel("User ID");
        idLabel.setLabelFor(id);
        form.add(idLabel);
        Theme.field(id);
        form.add(id);
        JLabel passwordLabel=new JLabel("Password");
        passwordLabel.setLabelFor(password);
        form.add(passwordLabel);
        Theme.field(password);
        form.add(password);
        form.add(submit);
        JLabel state=new JLabel("Data is retained for this application session only.");
        form.add(state);
        add(form);
        Runnable signIn=()-> {
            if(!submit.isEnabled())return;
            char[] chars=password.getPassword();
            String secret=new String(chars);
            Arrays.fill(chars, '\0');
            if(id.getText().isBlank()||secret.isEmpty()) {
                MessagePopup.warning(this, "Enter your user ID and password.");
                return;
            }
            String userId=id.getText().trim();
            submit.setEnabled(false);
            state.setText("Signing in...");
            password.setText("");
            AsyncLoader.load(()->app.login.login(userId, secret), result-> {
                submit.setEnabled(true);
                state.setText("Data is retained for this application session only.");
                if(result.isPresent()) {
                    attempts=0;
                    authenticated.run();
                }
                else {
                    attempts++;
                    MessagePopup.error(this, new IllegalArgumentException("Invalid ID or password"));
                    if(attempts>=3)MessagePopup.warning(this, "Sign-in may be temporarily restricted. Wait one minute before retrying.");
                }
            }, e-> {
                submit.setEnabled(true);
                state.setText("Unable to sign in.");
                MessagePopup.error(this, e);
            });
        };
        submit.addActionListener(e->signIn.run());
        password.addActionListener(e->signIn.run());
        id.addActionListener(e->signIn.run());
        if(app.login.needsSetup()) {
            submit.setEnabled(false);
            JButton setup=Theme.button("Create first administrator", false);
            form.add(setup);
            setup.addActionListener(e->FormPopup.show(this, "First-run administrator setup", java.util.List.of(FormField.text("ID"), FormField.text("Name"), FormField.password()), v->app.login.bootstrapAdministrator(v.get("ID"), v.get("Name"), v.get("Password")), ()-> {
                setup.setVisible(false);
                submit.setEnabled(true);
                MessagePopup.info(this, "Administrator created. Sign in to continue.");
            }));
        }
    }
}
