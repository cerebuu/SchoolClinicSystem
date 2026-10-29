package clinic.app.ui.common;

import clinic.app.ApplicationContext;
import clinic.model.Employee;
import clinic.model.Patient;
import clinic.model.Student;
import clinic.service.AuditLog;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** Debounce, privacy, Enter, and stale-result checks for reusable patient lookup. */
public final class PatientLookupFieldChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        try(ApplicationContext app=new ApplicationContext(true,new AuditLog())) {
            app.login.login("N001","nurse123").orElseThrow();
            Student student=new Student("S-1","Student Name","09171234560","BSCS",2,"CS-2A");
            student.setGuardianContact("09171234567");
            Student noGuardian=new Student("S-2","No Guardian","09171112222","BSCS",1,"CS-1A");
            Employee employee=new Employee("E-1","Employee Name","jane.doe@gmail.com","Admin","Nurse");
            app.clinic().registerPatient(student);app.clinic().registerPatient(noGuardian);app.clinic().registerPatient(employee);
            regularLookups(app);
            debounceAndEnter(app,student,employee);
        }
        System.out.println("PASS: "+checks+" patient lookup field checks.");
    }

    private static void regularLookups(ApplicationContext app) throws Exception {
        PatientLookupField field=create(app,null);
        type(field,"6767");waitFor(()->field.statusText().equals("No patient with this ID"));state("unknown ID",field);
        check(!field.isFormReady(),"unknown patient stays disabled: "+field.statusText());
        type(field,"S-1");waitFor(field::isFormReady);
        String student=field.statusText();state("valid student",field);
        check(student.contains("Student")&&student.contains("Section: CS-2A"),"student summary includes type and section: "+student);
        check(!student.contains("09171234567"),
            "patient lookup status does not expose contact values");
        type(field," s-1 ");waitFor(field::isFormReady);state("trimmed lowercase ID",field);
        type(field,"S-2");waitFor(field::isFormReady);state("student without guardian",field);
        check(field.foundPatient().orElseThrow().getContactInfo().equals("09171112222"),
            "patient lookup accepts a found profile without requiring a guardian contact");
        type(field,"E-1");waitFor(field::isFormReady);state("employee contact",field);
        check(field.statusText().contains("Position: Nurse")&&!field.statusText().contains("jane.doe@gmail.com"),
            "patient lookup status does not expose employee contact values");
        field.close();
    }

    private static void debounceAndEnter(ApplicationContext app,Student student,Employee employee) throws Exception {
        AtomicReference<Map<String,Patient>> patients=new AtomicReference<>(Map.of("S-1",student,"E-1",employee));
        PatientLookupField fast=create(app,id->Optional.ofNullable(patients.get().get(id)));
        type(fast,"missing");Thread.sleep(60);type(fast,"S-1");waitFor(fast::isFormReady);state("rapid typing",fast);
        check(fast.lookupCount()==1,"rapid typing launches only the final lookup");fast.close();

        PatientLookupField stale=create(app,id->{
            if(id.equals("slow"))sleepIgnoringCancel(650);
            return Optional.ofNullable(patients.get().get(id));
        });
        type(stale,"slow");Thread.sleep(460);type(stale,"E-1");waitFor(stale::isFormReady);
        state("stale result ignored",stale);
        check(stale.statusText().contains("Position: Nurse"),
            "a delayed stale result cannot replace the newer employee result");
        check(stale.lookupCount()==2,"stale-result case ran both lookups");stale.close();

        PatientLookupField enter=create(app,id->Optional.ofNullable(patients.get().get(id)));
        SwingUtilities.invokeAndWait(()->{enter.textField().setText("S-1");enter.textField().postActionEvent();});
        waitFor(enter::isFormReady);state("Enter lookup",enter);
        check(enter.lookupCount()==1,"Enter starts lookup immediately and cancels debounce");enter.close();
    }

    private static PatientLookupField create(ApplicationContext app,
            java.util.function.Function<String,Optional<Patient>> finder) throws Exception {
        AtomicReference<PatientLookupField> result=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->result.set(finder==null?new PatientLookupField(app,
            PatientLookupField.Mode.EXISTING,"Patient ID"):
            new PatientLookupField(app,PatientLookupField.Mode.EXISTING,"Patient ID",finder)));
        return result.get();
    }

    private static void type(PatientLookupField field,String value) throws Exception {
        SwingUtilities.invokeAndWait(()->field.textField().setText(value));
    }
    private static void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
        while(System.nanoTime()<deadline) {
            AtomicReference<Boolean> result=new AtomicReference<>(false);
            SwingUtilities.invokeAndWait(()->result.set(condition.getAsBoolean()));
            if(result.get())return;
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for patient lookup result.");
    }
    private static void sleepIgnoringCancel(long millis) {
        long end=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(millis);
        while(System.nanoTime()<end)try{Thread.sleep(15);}catch(InterruptedException ignored) { }
    }
    private static void state(String label,PatientLookupField field) throws Exception {
        SwingUtilities.invokeAndWait(()->System.out.println("FIELD "+label+" | Patient ID="+
            field.textField().getText()+" | Status="+field.statusText()+" | Save enabled="+field.isFormReady()+
            " | lookup count="+field.lookupCount()));
    }
    private static void check(boolean value,String text) { if(!value)throw new AssertionError(text);checks++; }
}
