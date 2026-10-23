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
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_8, Theme.SPACE_8,
            Theme.SPACE_8, Theme.SPACE_8));
        form.setPreferredSize(new Dimension(510, app.login.needsSetup() ? 520 : 460));
        JLabel logo=new JLabel("  UIC School Clinic",
            new IconPainter("dashboard", Theme.BRAND), JLabel.LEFT);
        logo.setFont(Theme.font(Theme.TYPE_TITLE, Font.BOLD));
        logo.setForeground(Theme.INK);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle=new JLabel("Management System");
        subtitle.setFont(Theme.font(Theme.TYPE_BODY, Font.PLAIN));
        subtitle.setForeground(Theme.MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel signInTitle=new JLabel("Welcome back");
        signInTitle.setFont(Theme.font(Theme.TYPE_PAGE, Font.BOLD));
        signInTitle.setForeground(Theme.INK);
        signInTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(logo);
        form.add(Box.createVerticalStrut(Theme.SPACE_1));
        form.add(subtitle);
        form.add(Box.createVerticalStrut(Theme.SPACE_8));
        form.add(signInTitle);
        form.add(Box.createVerticalStrut(Theme.SPACE_4));
        JLabel idLabel=new JLabel("User ID");
        idLabel.setLabelFor(id);
        idLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(idLabel);
        Theme.field(id);
        id.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.FIELD_HEIGHT));
        id.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(id);
        form.add(Box.createVerticalStrut(Theme.SPACE_3));
        JLabel passwordLabel=new JLabel("Password");
        passwordLabel.setLabelFor(password);
        passwordLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(passwordLabel);
        Theme.field(password);
        password.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.FIELD_HEIGHT));
        password.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(password);
        form.add(Box.createVerticalStrut(Theme.SPACE_4));
        submit.setAlignmentX(Component.LEFT_ALIGNMENT);
        submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.CONTROL_HEIGHT));
        form.add(submit);
        JLabel state=new JLabel(app.storageDescription());
        state.setFont(Theme.font(Theme.TYPE_CAPTION, Font.PLAIN));
        state.setForeground(Theme.MUTED);
        state.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel error=new JLabel(" ");
        error.setFont(Theme.font(Theme.TYPE_BODY, Font.BOLD));
        error.setForeground(Theme.DANGER);
        error.setAlignmentX(Component.LEFT_ALIGNMENT);
        error.setName("Sign-in error");
        form.add(Box.createVerticalStrut(Theme.SPACE_2));
        form.add(error);
        form.add(state);
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
        if(app.login.needsSetup()) {
            submit.setEnabled(false);
            JButton setup=Theme.button("Create first administrator", false);
            setup.setAlignmentX(Component.LEFT_ALIGNMENT);
            setup.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.CONTROL_HEIGHT));
            form.add(Box.createVerticalStrut(Theme.SPACE_2));
            form.add(setup);
            setup.addActionListener(e->FormPopup.show(this, "First-run administrator setup", java.util.List.of(FormField.text("ID"), FormField.text("Name"), FormField.password()), v->app.login.bootstrapAdministrator(v.get("ID"), v.get("Name"), v.get("Password")), ()-> {
                setup.setVisible(false);
                submit.setEnabled(true);
                MessagePopup.info(this, "Administrator created. Sign in to continue.");
            }));
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setPaint(new GradientPaint(0, 0, Theme.HEADER_TINT,
            getWidth(), getHeight(), Theme.BRAND_TINT));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.dispose();
        super.paintComponent(graphics);
    }
}
