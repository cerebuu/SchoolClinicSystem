package clinic.app.ui.admin;
import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.*;
import clinic.model.User;
import clinic.model.Campus;
import clinic.model.NurseCampus;
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
        return List.of(
            new RowAction("View account details",()->showDetails(user,active)),
            new RowAction("Edit user / role",()->editUser(user)),
            new RowAction(active?"Deactivate":"Activate",()->setUserActive(user,active)),
            new RowAction("Account status",()->Actions.run(this,
                ()->app.login.isActive(user.getUserId()),
                current->MessagePopup.info(this,current?"Account is active.":"Account is inactive."))));
    }

    private void showDetails(User user,boolean active) {
        String campus=user.getCampusId()==null?"Unassigned":Objects.toString(app.admin.campusName(user.getCampusId()),user.getCampusId());
        MessagePopup.info(this,"User ID: "+user.getUserId()+"\nName: "+user.getName()+"\nRole: "+user.getRole()
            +"\nAssigned School / Campus: "+campus+"\nAccount status: "+(active?"Active":"Inactive"));
    }

    private void editUser(User user) {
        Actions.run(this,()->new UserFormData(app.login.isActive(user.getUserId()),campusChoices()),data->{
            if(isShowing())showForm(user,data.active(),data.campuses());
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
        Actions.run(this, this::campusChoices, campuses -> {
            if (isShowing()) showForm(user, active, campuses);
        });
    }

    private void showForm(User user, boolean active, List<Campus> campuses) {
        List<FormField> fields=new ArrayList<>();
        if(user==null)fields.add(FormField.text("ID"));
        fields.add(FormField.value("Name", user==null?"":user.getName()));
        String roleInitial=user==null?User.NURSE:user.getRole();
        fields.add(new FormField("Role", roleInitial, true, false, new String[] {
            User.ADMIN, User.NURSE, User.ASSISTANT, User.DOCTOR
        }));
        List<String> campusChoices=campuses.stream()
            .map(campus->campus.id()+" | "+campus.name()).toList();
        String campusInitialId=user==null?null:user.getCampusId();
        JComboBox<String> roleField=new JComboBox<>(new String[] {
            User.ADMIN, User.NURSE, User.ASSISTANT, User.DOCTOR
        });
        roleField.setSelectedItem(roleInitial);
        JComboBox<String> campusField=new JComboBox<>();
        populateCampusChoices(campusField,campusChoices,roleInitial,campusInitialId);
        roleField.addActionListener(e->populateCampusChoices(campusField,campusChoices,
            Objects.toString(roleField.getSelectedItem(),""),selectedCampusId(campusField.getSelectedItem())));
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
                if(User.NURSE.equals(v.get("Role")))
                    replacement.setCampusId(NurseCampus.forName(campus).orElseThrow(()->
                        new IllegalArgumentException("Choose one of the three approved UIC campuses.")).id());
                else if(!"Unassigned".equals(campus)&&campus!=null&&!campus.isBlank())
                    replacement.setCampusId(campus.substring(0,campus.indexOf(" | ")));
                if(user==null)app.admin.addUser(replacement,v.get("Password"));
                else app.admin.updateUser(replacement,"Yes".equals(v.get("Active")));
                return true;
            }),()->{});
    }

    private static String selectedCampusId(Object selected) {
        if(selected==null)return null;
        String label=selected.toString();
        var approved=NurseCampus.forName(label);
        if(approved.isPresent())return approved.get().id();
        int separator=label.indexOf(" | ");
        return separator>0?label.substring(0,separator):null;
    }

    private static void populateCampusChoices(JComboBox<String> field,List<String> campuses,
            String role,String preferredId) {
        field.removeAllItems();
        boolean administrator=User.ADMIN.equals(role);
        if(administrator) {
            field.addItem("Unassigned");
            campuses.forEach(field::addItem);
            if(preferredId!=null)selectCampus(field,preferredId);
            else field.setSelectedItem("Unassigned");
        } else if(User.NURSE.equals(role)) {
            NurseCampus.choices().forEach(campus->field.addItem(campus.displayName()));
            NurseCampus.forId(preferredId).ifPresent(campus->field.setSelectedItem(campus.displayName()));
            if(preferredId==null||NurseCampus.forId(preferredId).isEmpty())field.setSelectedIndex(-1);
        } else {
            field.addItem("Unassigned");
            campuses.forEach(field::addItem);
            if(preferredId==null)field.setSelectedItem("Unassigned");
            else selectCampus(field,preferredId);
        }
    }

    private static void selectCampus(JComboBox<String> field,String preferredId) {
        if(preferredId==null)return;
        for(int i=0;i<field.getItemCount();i++) {
            String item=field.getItemAt(i);
            if(item.equals(preferredId)||item.startsWith(preferredId+" | ")) {
                field.setSelectedIndex(i);
                return;
            }
        }
        field.setSelectedIndex(-1);
    }

    private List<Campus> campusChoices() {
        List<Campus> result=new ArrayList<>();
        int page=0,total;
        do {
            var next=app.admin.campuses(page++,200,"");
            result.addAll(next.rows());
            total=next.total();
            if(page*200>total+200)throw new IllegalStateException("Unable to load the campus list.");
        } while(result.size()<total);
        return List.copyOf(result);
    }

    private record UserFormData(boolean active, List<Campus> campuses) {}
}
