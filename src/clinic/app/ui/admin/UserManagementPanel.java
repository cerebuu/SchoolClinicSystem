package clinic.app.ui.admin;
import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.*;
import clinic.model.User;
import clinic.service.AdminService;
import java.awt.BorderLayout;
import java.util.*;
import javax.swing.JComboBox;
import javax.swing.JComponent;
public final class UserManagementPanel extends BasePanel {
    private final ApplicationContext app;
    private final PagedTablePanel<User> table;
    public UserManagementPanel(ApplicationContext app) {
        super("User management");
        this.app=app;
        table=new PagedTablePanel<>(new UsersTableModel(app.admin::campusName), app.admin::users);
        table.refreshOn(app.changes, clinic.app.DataTopic.USERS);
        table.setRowActions(this::userActions);
        add(table, BorderLayout.CENTER);
        action("Add user", true, ()->openForm(null, true));
    }

    private List<RowAction> userActions(User user) {
        boolean active=app.login.isActive(user.getUserId());
        List<RowAction> actions=new ArrayList<>(List.of(
            new RowAction("View account details",()->showDetails(user,active)),
            new RowAction("Edit user / role",()->editUser(user)),
            new RowAction(active?"Deactivate":"Activate",()->setUserActive(user,active)),
            new RowAction("Account status",()->Actions.run(this,
                ()->app.login.isActive(user.getUserId()),
                current->MessagePopup.info(this,current?"Account is active.":"Account is inactive.")))));
        if(Set.of(User.NURSE,User.DOCTOR,User.ASSISTANT).contains(user.getRole()))
            actions.add(new RowAction("Reset password",()->resetPassword(user)));
        return List.copyOf(actions);
    }

    private void resetPassword(User user) {
        FormPopup.show(this,"Reset password for "+user.getUserId(),List.of(
            FormField.password(),new FormField("Confirm password","",true,true,null)),values->{
                if(!values.get("Password").equals(values.get("Confirm password")))
                    throw new IllegalArgumentException("Passwords do not match.");
                app.admin.resetStaffPassword(user.getUserId(),values.get("Password"));
            },()->MessagePopup.info(this,"Password reset for "+user.getUserId()+"."));
    }

    private void showDetails(User user,boolean active) {
        String campus=user.getCampusId()==null?"Unassigned":Objects.toString(app.admin.campusName(user.getCampusId()),user.getCampusId());
        MessagePopup.infoWithCopy(this,"User account details","User ID: "+user.getUserId()+"\nName: "+user.getName()+"\nRole: "+user.getRole()
            +"\nAssigned School / Campus: "+campus+"\nAccount status: "+(active?"Active":"Inactive"));
    }

    private void editUser(User user) {
        Actions.run(this,()->app.login.isActive(user.getUserId()),active->{
            if(isShowing())showForm(user,active);
        });
    }

    private void setUserActive(User user,boolean active) {
        if(active&&!MessagePopup.confirm(this,"Deactivate account "+user.getUserId()+"?"))return;
        Actions.run(this,()->{
            app.admin.updateUser(user,!active);
            return true;
        },done->{});
    }
    private void openForm(User user, boolean active) {
        showForm(user,active);
    }

    private void showForm(User user, boolean active) {
        List<FormField> fields=new ArrayList<>();
        if(user==null)fields.add(FormField.text("ID"));
        fields.add(FormField.value("Name", user==null?"":user.getName()));
        String roleInitial=user==null?User.NURSE:user.getRole();
        fields.add(new FormField("Role", roleInitial, true, false, new String[] {
            User.ADMIN, User.NURSE, User.ASSISTANT, User.DOCTOR
        }));
        String campusInitialId=user==null?null:user.getCampusId();
        JComboBox<String> roleField=new JComboBox<>(new String[] {
            User.ADMIN, User.NURSE, User.ASSISTANT, User.DOCTOR
        });
        roleField.setSelectedItem(roleInitial);
        CampusChoiceField campusField=new CampusChoiceField(app,roleInitial,campusInitialId);
        roleField.addActionListener(e->campusField.setRole(Objects.toString(roleField.getSelectedItem(),"")));
        fields.add(new FormField("Assigned School / Campus","",true,false,null));
        if(user==null)fields.add(FormField.password());
        else fields.add(new FormField("Active", active?"Yes":"No", true, false, new String[] {
            "Yes", "No"
        }));
        Map<String,JComponent> inputs=new LinkedHashMap<>();
        inputs.put("Role",roleField);
        inputs.put("Assigned School / Campus",campusField);
        FormPopup.showWithComponents(this,user==null?"Add user":"Edit user "+user.getUserId(),
            fields,inputs,(v,selections,controller)->controller.saveAsync(()-> {
                User replacement=AdminService.user(user==null?v.get("ID"):user.getUserId(),
                    v.get("Name"),v.get("Role"));
                String campus=v.get("Assigned School / Campus");
                if(!"Unassigned".equals(campus)&&campus!=null&&!campus.isBlank())
                    replacement.setCampusId(campus);
                if(user==null)app.admin.addUser(replacement,v.get("Password"));
                else app.admin.updateUser(replacement,"Yes".equals(v.get("Active")));
                return true;
            }),()->{});
    }

}
