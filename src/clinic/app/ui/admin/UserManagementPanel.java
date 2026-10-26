package clinic.app.ui.admin;
import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.*;
import clinic.model.User;
import clinic.service.AdminService;
import java.awt.BorderLayout;
import java.util.*;
public final class UserManagementPanel extends BasePanel {
    private final ApplicationContext app;
    private final PagedTablePanel<User> table;
    public UserManagementPanel(ApplicationContext app) {
        super("User management");
        this.app=app;
        table=new PagedTablePanel<>(new UsersTableModel(), app.admin::users);
        table.refreshOn(app.changes, clinic.app.DataTopic.USERS);
        add(table, BorderLayout.CENTER);
        action("Add user", true, ()->form(null));
        action("Edit user / role", ()-> {
            User u=table.selected();
            if(u==null) {
                MessagePopup.warning(this, "Select a user.");
                return;
            }
            Actions.run(this, ()->app.login.isActive(u.getUserId()), active->form(u, active));
        });
        action("Deactivate", ()-> {
            User u=table.selected();
            if(u==null) {
                MessagePopup.warning(this, "Select a user.");
                return;
            }
            if(MessagePopup.confirm(this, "Deactivate account "+u.getUserId()+"?"))Actions.run(this, ()-> {
                app.admin.updateUser(u, false);
                return true;
            }, ok->{});
        });
        action("Account status", ()-> {
            User u=table.selected();
            if(u==null) {
                MessagePopup.warning(this, "Select a user.");
                return;
            }
            Actions.run(this, ()->app.login.isActive(u.getUserId()), active->MessagePopup.info(this, active?"Account is active.":"Account is inactive."));
        });
    }
    private void form(User user) {
        form(user, true);
    }
    private void form(User user, boolean active) {
        List<FormField> fields=new ArrayList<>();
        if(user==null)fields.add(FormField.text("ID"));
        fields.add(FormField.value("Name", user==null?"":user.getName()));
        fields.add(new FormField("Role", user==null?User.NURSE:user.getRole(), true, false, new String[] {
            User.ADMIN, User.NURSE, User.ASSISTANT, User.DOCTOR
        }));
        if(user==null)fields.add(FormField.password());
        else fields.add(new FormField("Active", active?"Yes":"No", true, false, new String[] {
            "Yes", "No"
        }));
        FormPopup.show(this, user==null?"Add user":"Edit user "+user.getUserId(), fields, v-> {
            User replacement=AdminService.user(user==null?v.get("ID"):user.getUserId(), v.get("Name"), v.get("Role"));
            if(user==null)app.admin.addUser(replacement, v.get("Password"));
            else app.admin.updateUser(replacement, "Yes".equals(v.get("Active")));
        }, ()->{});
    }
}
