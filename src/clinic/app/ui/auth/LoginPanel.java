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
        setOpaque(false);
        Card form=new Card();
        form.setLayout(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_6, Theme.SPACE_8,
            Theme.SPACE_6, Theme.SPACE_8));
        form.setPreferredSize(new Dimension(560, app.login.needsSetup() ? 660 : 620));
        JLabel seal=UicLogo.label(144);
        JLabel appName=new JLabel("UIC School Clinic");
        appName.setFont(Theme.font(Theme.TYPE_TITLE, Font.BOLD));
        appName.setForeground(Theme.INK);
        JLabel appSubtitle=new JLabel("Management System");
        appSubtitle.setFont(Theme.font(Theme.TYPE_BODY, Font.PLAIN));
        appSubtitle.setForeground(Theme.MUTED);
        JLabel signInTitle=new JLabel("Welcome back");
        signInTitle.setFont(Theme.font(Theme.TYPE_PAGE, Font.BOLD));
        signInTitle.setForeground(Theme.INK);
        JLabel signInSubtitle=new JLabel("Sign in to access your clinic workspace.");
        signInSubtitle.setFont(Theme.font(Theme.TYPE_BODY, Font.PLAIN));
        signInSubtitle.setForeground(Theme.MUTED);
        JLabel idLabel=new JLabel("User ID");
        idLabel.setLabelFor(id);
        Theme.field(id);
        id.getAccessibleContext().setAccessibleName("User ID");
        id.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.FIELD_HEIGHT));
        JLabel passwordLabel=new JLabel("Password");
        passwordLabel.setLabelFor(password);
        Theme.field(password);
        password.getAccessibleContext().setAccessibleName("Password");
        password.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.FIELD_HEIGHT));
        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.CONTROL_HEIGHT));
        submit.getAccessibleContext().setAccessibleName("Sign in");
        JLabel state=new JLabel(app.storageDescription());
        state.setFont(Theme.font(Theme.TYPE_CAPTION, Font.PLAIN));
        state.setForeground(Theme.MUTED);
        JLabel error=new JLabel(" ");
        error.setFont(Theme.font(Theme.TYPE_BODY, Font.BOLD));
        error.setForeground(Theme.DANGER);
        error.setName("Sign-in error");
        JButton setup=app.login.needsSetup()?Theme.button("Create first administrator", false):null;
        if(setup!=null) {
            submit.setEnabled(false);
            setup.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.CONTROL_HEIGHT));
            setup.getAccessibleContext().setAccessibleName("Create first administrator");
        }
        int row=0;
        addRow(form, seal, row++, 0, Theme.SPACE_2, false, true);
        addRow(form, appName, row++, 0, Theme.SPACE_1, false, true);
        addRow(form, appSubtitle, row++, 0, Theme.SPACE_6, false, true);
        addRow(form, signInTitle, row++, 0, Theme.SPACE_1, true, false);
        addRow(form, signInSubtitle, row++, 0, Theme.SPACE_4, true, false);
        addRow(form, idLabel, row++, 0, Theme.SPACE_1, true, false);
        addRow(form, id, row++, 0, Theme.SPACE_2, true, false);
        addRow(form, passwordLabel, row++, 0, Theme.SPACE_1, true, false);
        addRow(form, password, row++, 0, Theme.SPACE_4, true, false);
        addRow(form, submit, row++, 0, Theme.SPACE_2, true, false);
        if(setup!=null) addRow(form, setup, row++, 0, Theme.SPACE_2, true, false);
        addRow(form, error, row++, 0, Theme.SPACE_1, true, false);
        addRow(form, state, row, 0, 0, true, false);
        GridBagConstraints cardConstraints = new GridBagConstraints();
        cardConstraints.gridx = 0;
        cardConstraints.gridy = 0;
        cardConstraints.weightx = 1;
        cardConstraints.weighty = 1;
        cardConstraints.anchor = GridBagConstraints.CENTER;
        add(form, cardConstraints);
        Runnable signIn=()-> {
            if(!submit.isEnabled())return;
            char[] chars=password.getPassword();
            String secret=new String(chars);
            Arrays.fill(chars, '\0');
            if(id.getText().isBlank()||secret.isEmpty()) {
                error.setText("Enter your user ID and password.");
                return;
            }
            String userId=id.getText().trim();
            error.setText(" ");
            submit.setEnabled(false);
            state.setText("Signing in...");
            password.setText("");
            AsyncLoader.load(()->app.login.login(userId, secret), result-> {
                submit.setEnabled(true);
                state.setText(app.storageDescription());
                if(result.isPresent()) {
                    attempts=0;
                    authenticated.run();
                }
                else {
                    attempts++;
                    error.setText(attempts>=3
                        ? "Sign-in failed. Wait one minute before retrying."
                        : "Invalid user ID or password.");
                }
            }, e-> {
                submit.setEnabled(true);
                state.setText("Unable to sign in.");
                error.setText("Unable to sign in. Please retry.");
                MessagePopup.error(this, e);
            });
        };
        submit.addActionListener(e->signIn.run());
        password.addActionListener(e->signIn.run());
        id.addActionListener(e->signIn.run());
        if(setup!=null) {
            setup.addActionListener(e->FormPopup.show(this, "First-run administrator setup", java.util.List.of(FormField.text("ID"), FormField.text("Name"), FormField.password()), v->app.login.bootstrapAdministrator(v.get("ID"), v.get("Name"), v.get("Password")), ()-> {
                setup.setVisible(false);
                submit.setEnabled(true);
                MessagePopup.info(this, "Administrator created. Sign in to continue.");
            }));
        }
    }

    private static void addRow(JPanel panel, Component component, int row,
            int topInset, int bottomInset, boolean fillWidth, boolean centered) {
        GridBagConstraints constraints=new GridBagConstraints();
        constraints.gridx=0;
        constraints.gridy=row;
        constraints.weightx=1;
        constraints.fill=fillWidth?GridBagConstraints.HORIZONTAL:GridBagConstraints.NONE;
        constraints.anchor=centered?GridBagConstraints.CENTER:GridBagConstraints.WEST;
        constraints.insets=new Insets(topInset,0,bottomInset,0);
        panel.add(component,constraints);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setPaint(new GradientPaint(0, 0, Theme.SUBTLE,
            getWidth(), getHeight(), Theme.BRAND_TINT));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.dispose();
        super.paintComponent(graphics);
    }
}
