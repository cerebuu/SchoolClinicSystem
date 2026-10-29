package clinic.app.ui.admin;
import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.*;
import clinic.model.Campus;
import java.awt.BorderLayout;
import java.util.*;
public final class CampusManagementPanel extends BasePanel {
    private final ApplicationContext app;
    private final PagedTablePanel<Campus> table;
    public CampusManagementPanel(ApplicationContext app) {
        super("Campus management");
        this.app=app;
        table=new PagedTablePanel<>(new CampusesTableModel(), app.admin::campuses);
        table.refreshOn(app.changes, clinic.app.DataTopic.CAMPUSES);
        table.setRowActions(campus->List.of(
            new RowAction("Edit campus",()->form(campus)),
            new RowAction("Delete campus",()->delete(campus))));
        add(table, BorderLayout.CENTER);
        action("Add campus", true, ()->form(null));
    }
    private void form(Campus campus) {
        List<FormField> fields=new ArrayList<>();
        if(campus==null)fields.add(FormField.text("Campus ID"));
        fields.add(FormField.value("Name", campus==null?"":campus.name()));
        FormPopup.show(this, campus==null?"Add campus":"Edit campus", fields, v->app.admin.saveCampus(new Campus(campus==null?v.get("Campus ID"):campus.id(), v.get("Name")), campus!=null), ()->{});
    }
    private void delete(Campus campus) {
        if(!MessagePopup.confirm(this,"Remove campus "+campus.name()+"? Existing account assignments will be marked invalid."))return;
        Actions.run(this,()->{app.admin.deleteCampus(campus.id());return true;},removed->{});
    }
}
