package clinic.app.ui.admin;

import clinic.app.ApplicationContext;
import clinic.app.DataTopic;
import clinic.app.Theme;
import clinic.app.ui.common.AsyncLoader;
import clinic.app.ui.common.popup.FormValueComponent;
import clinic.model.Campus;
import clinic.model.NurseCampus;
import clinic.model.User;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Loads current campus choices and keeps invalid assignments out of the dropdown. */
final class CampusChoiceField extends JPanel implements FormValueComponent,AutoCloseable {
    private static final String UNASSIGNED="Unassigned";
    private final ApplicationContext app;
    private final JComboBox<String> choices=new JComboBox<>();
    private final JLabel status=new JLabel("Loading campuses...");
    private final DataTopic topic=DataTopic.CAMPUSES;
    private final clinic.app.DataChangeBus.Subscription subscription;
    private String role;
    private String preferredId;
    private List<Campus> campuses=List.of();
    private int generation;
    private boolean loaded,failed,closed;

    CampusChoiceField(ApplicationContext app,String role,String preferredId) {
        super(new BorderLayout(0,Theme.SPACE_1));
        this.app=Objects.requireNonNull(app);
        this.role=Objects.requireNonNull(role);
        this.preferredId=preferredId;
        setOpaque(false);
        choices.setName("Campus choices");
        choices.getAccessibleContext().setAccessibleName("Assigned School / Campus");
        Theme.field(choices);
        add(choices,BorderLayout.CENTER);
        status.setFont(Theme.font(Theme.TYPE_CAPTION,Font.PLAIN));
        status.setForeground(Theme.MUTED);
        add(status,BorderLayout.SOUTH);
        setPreferredSize(new Dimension(380,Theme.FIELD_HEIGHT+24));
        choices.addActionListener(event->selectionChanged());
        subscription=app.changes.subscribe(this::reload,topic);
        reload();
    }

    void setRole(String value) {
        String selection=selectedId();
        if(selection!=null)preferredId=selection;
        role=Objects.requireNonNull(value);
        if(loaded)populate();
    }

    private void reload() {
        if(closed)return;
        int request=++generation;
        loaded=false;failed=false;
        status.setText("Loading campuses...");
        status.setForeground(Theme.MUTED);
        fireReadinessChanged();
        AsyncLoader.load(this::loadCampuses,result->{
            if(closed||request!=generation)return;
            campuses=result;loaded=true;failed=false;populate();
        },failure->{
            if(closed||request!=generation)return;
            loaded=true;failed=true;choices.removeAllItems();
            status.setText("Campus list could not be loaded.");
            status.setForeground(Theme.DANGER);
            fireReadinessChanged();
        });
    }

    private List<Campus> loadCampuses() {
        List<Campus> result=new ArrayList<>();
        int page=0,total;
        do {
            var next=app.admin.campuses(page++,200,"");
            result.addAll(next.rows());
            total=next.total();
            if(result.size()<total&&next.rows().isEmpty())
                throw new IllegalStateException("Unable to load the campus list.");
        } while(result.size()<total);
        return List.copyOf(result);
    }

    private void populate() {
        boolean readyBefore=isFormReady();
        String desired=selectedId();
        if(desired==null)desired=preferredId;
        choices.removeAllItems();
        if(User.ADMIN.equals(role))choices.addItem(UNASSIGNED);
        for(Campus campus:campuses) {
            if(!User.NURSE.equals(role)||NurseCampus.forId(campus.id()).isPresent())
                choices.addItem(format(campus));
        }
        if(desired==null&&User.ADMIN.equals(role))choices.setSelectedItem(UNASSIGNED);
        else if(desired==null)choices.setSelectedIndex(-1);
        else select(desired);
        String invalidId=desired;
        boolean exists=campuses.stream().anyMatch(campus->campus.id().equals(invalidId));
        boolean offered=containsId(invalidId);
        if(invalidId!=null&&!exists) {
            status.setText("Invalid campus assignment: "+invalidId);
            status.setForeground(Theme.DANGER);
        } else if(invalidId!=null&&!offered) {
            status.setText("This role cannot use the assigned campus. Choose an approved campus.");
            status.setForeground(Theme.DANGER);
        } else if(!User.ADMIN.equals(role)&&selectedId()==null) {
            status.setText("Choose a campus.");
            status.setForeground(Theme.MUTED);
        } else {
            status.setText(" ");
            status.setForeground(Theme.MUTED);
        }
        if(selectedId()!=null)preferredId=selectedId();
        else if(desired==null)preferredId=null;
        firePropertyChange(READY_PROPERTY,readyBefore,isFormReady());
        revalidate();
    }

    private void select(String id) {
        if(id==null)return;
        for(int i=0;i<choices.getItemCount();i++)
            if(choices.getItemAt(i).startsWith(id+" | ")) {
                choices.setSelectedIndex(i);
                return;
            }
        choices.setSelectedIndex(-1);
    }

    private boolean containsId(String id) {
        if(id==null)return false;
        for(int i=0;i<choices.getItemCount();i++)
            if(choices.getItemAt(i).startsWith(id+" | "))return true;
        return false;
    }

    private String selectedId() {
        Object selected=choices.getSelectedItem();
        if(!(selected instanceof String label))return null;
        int separator=label.indexOf(" | ");
        return separator<0?null:label.substring(0,separator);
    }

    private static String format(Campus campus) { return campus.id()+" | "+campus.name(); }

    private void selectionChanged() {
        if(!loaded||failed)return;
        String id=selectedId();
        if(id!=null) {
            preferredId=id;
            status.setText(" ");
            status.setForeground(Theme.MUTED);
        } else if(!User.ADMIN.equals(role)) {
            status.setText("Choose a campus.");
            status.setForeground(Theme.MUTED);
        }
        firePropertyChange(READY_PROPERTY,null,isFormReady());
    }

    @Override public String formValue() {
        Object selected=choices.getSelectedItem();
        return selectedId()!=null?selectedId():Objects.toString(selected,"");
    }

    @Override public boolean isFormReady() {
        if(!loaded||failed)return false;
        return User.ADMIN.equals(role)?UNASSIGNED.equals(choices.getSelectedItem())||selectedId()!=null
            :selectedId()!=null;
    }

    @Override public boolean validateForm() {
        if(!isFormReady()) {
            if(!failed) {
                status.setText("Choose a valid campus assignment.");
                status.setForeground(Theme.DANGER);
            }
            return false;
        }
        return true;
    }

    private void fireReadinessChanged() {
        firePropertyChange(READY_PROPERTY,null,isFormReady());
    }

    JComboBox<String> comboBox() { return choices; }
    String statusText() { return status.getText(); }

    @Override public void close() {
        closed=true;generation++;subscription.close();
    }
}
