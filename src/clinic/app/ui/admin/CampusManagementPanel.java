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
        add(table, BorderLayout.CENTER);
        action("Add campus", ()->form(null));
        action("Edit campus", ()-> {
            Campus c=table.selected();
            if(c==null) {
                MessagePopup.warning(this, "Select a campus.");
                return;
            }
            form(c);
        });
    }
    private void form(Campus campus) {
        List<FormField> fields=new ArrayList<>();
        if(campus==null)fields.add(FormField.text("Campus ID"));
        fields.add(FormField.value("Name", campus==null?"":campus.name()));
        FormPopup.show(this, campus==null?"Add campus":"Edit campus", fields, v->app.admin.saveCampus(new Campus(campus==null?v.get("Campus ID"):campus.id(), v.get("Name")), campus!=null), table::reload);
    }
}
