package clinic.app.ui.common;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.model.Employee;
import clinic.model.Patient;
import clinic.model.Student;
import clinic.model.User;
import clinic.core.ClinicSystem;
import clinic.app.ui.common.popup.FormValueComponent;
import java.awt.BorderLayout;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Debounced, session-safe patient ID lookup shared by clinic forms. */
public final class PatientLookupField extends JPanel implements FormValueComponent, AutoCloseable {
    public enum Mode { EXISTING, NEW_ID }
    public static final String PATIENT_PROPERTY="patient";

    private final ApplicationContext app;
    private final Mode mode;
    private final Function<String,Optional<Patient>> lookupOverride;
    private final JTextField input=new JTextField(18);
    private final JTextArea status=new JTextArea(3,32);
    private final JButton lookup;
    private final Timer debounce;
    private SwingWorker<Optional<Patient>,Void> worker;
    private Patient patient;
    private int generation;
    private volatile int lookupCount;
    private boolean ready,closed;

    public PatientLookupField(ApplicationContext app) { this(app,Mode.EXISTING,"Patient ID"); }

    public PatientLookupField(ApplicationContext app,Mode mode,String fieldName) {
        this(app,mode,fieldName,null);
    }

    PatientLookupField(ApplicationContext app,Mode mode,String fieldName,
            Function<String,Optional<Patient>> lookupOverride) {
        super(new BorderLayout(0,Theme.SPACE_1));
        this.app=java.util.Objects.requireNonNull(app);
        this.mode=java.util.Objects.requireNonNull(mode);
        this.lookupOverride=lookupOverride;
        setOpaque(false);
        input.setName(fieldName);
        input.getAccessibleContext().setAccessibleName(fieldName);
        Theme.field(input);
        lookup=Theme.button(mode==Mode.NEW_ID?"Check ID":"Find patient",false);
        JPanel row=new JPanel(new BorderLayout(Theme.GAP,0));
        row.setOpaque(false);row.add(input,BorderLayout.CENTER);row.add(lookup,BorderLayout.EAST);
        status.setName("Patient lookup status");
        status.setEditable(false);status.setFocusable(false);status.setLineWrap(true);status.setWrapStyleWord(true);
        status.setOpaque(false);status.setBorder(null);status.setFont(Theme.font(Theme.TYPE_CAPTION,java.awt.Font.PLAIN));
        status.setForeground(Theme.MUTED);status.setText("Enter an ID to check.");
        add(row,BorderLayout.NORTH);add(status,BorderLayout.CENTER);
        setPreferredSize(new java.awt.Dimension(380,Theme.FIELD_HEIGHT+52));
        debounce=new Timer(400,event->lookupNow());debounce.setRepeats(false);
        input.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event){changed();}
            public void removeUpdate(DocumentEvent event){changed();}
            public void changedUpdate(DocumentEvent event){changed();}
        });
        input.addActionListener(event->lookupNow());
        lookup.addActionListener(event->lookupNow());
    }

    private void changed() {
        generation++;
        cancelWorker();
        setPatient(null);
        setReady(false);
        String value=input.getText().trim();
        setStatus(value.isEmpty()?"Enter an ID to check.":"Waiting to check…",Theme.MUTED);
        debounce.stop();
        if(!value.isEmpty()&&!closed)debounce.restart();
    }

    private void lookupNow() {
        debounce.stop();
        String id=input.getText().trim();
        if(id.isEmpty()||closed)return;
        cancelWorker();
        int request=++generation;
        final LookupSession session;
        try {
            synchronized(app.login) {
                User owner=app.current();
                session=new LookupSession(app.clinic(),owner,app.login.getSessionVersion());
            }
        } catch(RuntimeException failure) {
            setReady(false);setPatient(null);
            setStatus("Sign in is required before patient lookup.",Theme.DANGER);
            return;
        }
        setReady(false);
        setStatus("Checking patient ID…",Theme.MUTED);
        lookupCount++;
        worker=new SwingWorker<>() {
            @Override protected Optional<Patient> doInBackground() {
                return lookupOverride==null?session.clinic.findPatient(id):lookupOverride.apply(id);
            }
            @Override protected void done() {
                if(isCancelled()||closed||request!=generation
                        ||!Actions.isCurrentSession(app.login,session.version,session.owner))return;
                try { complete(get().orElse(null)); }
                catch(java.util.concurrent.CancellationException ignored) { }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                catch(java.util.concurrent.ExecutionException failure) {
                    setPatient(null);setReady(false);
                    setStatus(mode==Mode.NEW_ID?"Could not check this ID. Try again.":"Patient lookup failed. Try again.",Theme.DANGER);
                }
            }
        };
        worker.execute();
    }

    private void complete(Patient found) {
        if(mode==Mode.NEW_ID) {
            setPatient(null);
            setReady(found==null);
            setStatus(found==null?"ID is available.":"This ID is already registered.",found==null?Theme.SUCCESS:Theme.DANGER);
            return;
        }
        if(found==null) {
            setPatient(null);setReady(false);setStatus("No patient with this ID",Theme.DANGER);return;
        }
        setPatient(found);
        setStatus(identity(found),Theme.INK);
        setReady(true);
    }

    private String identity(Patient value) {
        String type=value instanceof Employee?"Employee":"Student";
        String detail=value instanceof Student student?"Section: "+student.getSection()
            :value instanceof Employee employee?"Position: "+employee.getPosition():"";
        return detail.isEmpty()?"Patient found · "+type:"Patient found · "+type+" · "+detail;
    }

    private void setStatus(String value,java.awt.Color color) { status.setText(value);status.setForeground(color); }
    private void setPatient(Patient value) {
        Patient previous=patient;patient=value;
        firePropertyChange(PATIENT_PROPERTY,previous,value);
    }
    private void setReady(boolean value) {
        boolean previous=ready;ready=value;
        firePropertyChange(FormValueComponent.READY_PROPERTY,previous,value);
    }
    private void cancelWorker() { if(worker!=null){worker.cancel(true);worker=null;} }
    public JTextField textField() { return input; }
    public String statusText() { return status.getText(); }
    public boolean isFormReady() { return ready&&!closed; }
    int lookupCount() { return lookupCount; }
    public Optional<Patient> foundPatient() { return Optional.ofNullable(patient); }
    public String patientId() { return formValue(); }
    @Override public String formValue() {
        if(!isFormReady())throw new IllegalArgumentException("Confirm a patient ID before continuing.");
        return input.getText().trim();
    }
    public void clear() { input.setText(""); }
    @Override public void close() { closed=true;debounce.stop();cancelWorker();setReady(false);setPatient(null); }

    private record LookupSession(ClinicSystem clinic,User owner,long version) {}
}
