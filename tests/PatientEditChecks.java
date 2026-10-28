import clinic.app.ApplicationContext;
import clinic.core.ClinicSystem;
import clinic.model.*;
import clinic.service.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Focused patient-profile update checks; run with assertions enabled. */
public final class PatientEditChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        inMemoryUpdatePreservesLinkedData();
        persistentUpdatePreservesLinkedData();
        System.out.println("Patient edit checks passed: "+checks);
    }

    private static void inMemoryUpdatePreservesLinkedData() {
        InMemoryClinicSystem store=new InMemoryClinicSystem();
        Student original=patient("p-1","Old Name");
        store.registerPatient(original);
        store.addAllergy("p-1","Penicillin");
        store.recordVisit("p-1",List.of("Headache"),"Initial visit","Private history","n-1");
        NotificationLog notification=store.createEmergencyNotification("p-1");

        store.updatePatient("p-1",editedPatient("p-1","New Name"));
        Patient saved=store.findPatient("p-1").orElseThrow();
        check(saved.getName().equals("New Name"),"editable profile fields updated");
        check(saved.getContactInfo().equals("updated contact")&&saved.getGuardianContact().equals("updated guardian"),"contact fields updated");
        check(saved instanceof Student student&&student.getCourse().equals("BSIT")&&student.getSection().equals("C")&&student.getYearLevel()==3,"student details updated");
        check(saved.getId().equals("p-1")&&saved instanceof Student,"patient identity and type retained");
        check(saved.getAllergies().equals(List.of("Penicillin")),"allergies retained");
        check(saved.getMedicalHistory().equals(List.of("Existing history")),"medical history retained");
        check(store.getVisitHistory("p-1").size()==1,"linked visit retained");
        check(store.getNotifications("p-1").get(0).getNotificationId().equals(notification.getNotificationId()),"linked notification retained");
        rejects(IllegalArgumentException.class,()->store.updatePatient("p-1",patient("other","Wrong ID")),"ID changes rejected");
        rejects(IllegalArgumentException.class,()->store.updatePatient(" ",patient("p-1","Invalid ID")),"blank edit ID rejected");
        rejects(IllegalArgumentException.class,()->store.updatePatient("missing",patient("missing","Unknown")),"unknown patient rejected");
        rejects(IllegalArgumentException.class,()->store.updatePatient("p-1",new Employee("p-1","Changed type","contact","Clinic","Nurse")),"type changes rejected");
        rejects(IllegalArgumentException.class,()->store.registerPatient(new Student("p-1","Duplicate","contact","BSCS",2,"B")),"duplicate ID rejected");
        rejects(IllegalArgumentException.class,()->new Student(" ","Invalid","contact","BSCS",2,"B"),"blank ID rejected");

        SchoolNurse nurse=new SchoolNurse("n-1","Nurse");
        nurse.setCampusId("uic-bajada");
        ClinicSystem secured=new SecuredClinicSystem(store,nurse,new AuditLog());
        secured.updatePatient("p-1",editedPatient("p-1","Authorized change"));
        check(store.findPatient("p-1").orElseThrow().getName().equals("Authorized change"),"record transaction role may edit patients");
        Doctor doctor=new Doctor("d-1","Doctor","");
        doctor.setCampusId("uic-bajada");
        ClinicSystem restricted=new SecuredClinicSystem(store,doctor,new AuditLog());
        rejects(SecurityException.class,()->restricted.updatePatient("p-1",editedPatient("p-1","Denied change")),"unauthorized profile edit rejected");
    }

    private static void persistentUpdatePreservesLinkedData() throws Exception {
        Path directory=Files.createTempDirectory("scms-patient-edit-");
        Path databasePath=directory.resolve("clinic");
        String notificationId;
        try {
            try(ApplicationContext app=new ApplicationContext(databasePath)) {
                app.login.bootstrapAdministrator("root","Administrator","root-secret");
                app.login.login("root","root-secret").orElseThrow();
                app.admin.saveCampus(new Campus("main","Main campus"),false);
                SchoolNurse nurse=new SchoolNurse("n-1","Nurse");
                nurse.setCampusId("uic-main");
                app.admin.addUser(nurse,"nurse-secret");
                app.login.logout();
                app.login.login("n-1","nurse-secret").orElseThrow();
                ClinicSystem clinic=app.clinic();
                Student original=patient("db-p-1","Database Patient");
                clinic.registerPatient(original);
                clinic.addAllergy("db-p-1","Latex");
                clinic.recordVisit("db-p-1",List.of("Cough"),"Initial visit","History stays","n-1");
                notificationId=clinic.createEmergencyNotification("db-p-1").getNotificationId();
                clinic.updatePatient("db-p-1",editedPatient("db-p-1","Updated Database Patient"));
            }
            try(ApplicationContext app=new ApplicationContext(databasePath)) {
                app.login.login("n-1","nurse-secret").orElseThrow();
                ClinicSystem clinic=app.clinic();
                Patient saved=clinic.findPatient("db-p-1").orElseThrow();
                check(saved.getName().equals("Updated Database Patient"),"profile update persisted");
                check(Set.copyOf(saved.getAllergies()).equals(Set.of("Penicillin","Latex")),"persistent allergies retained");
                check(saved.getMedicalHistory().equals(List.of("Existing history")),"persistent history retained");
                check(clinic.getVisitHistory("db-p-1").size()==1,"persistent visit retained");
                check(clinic.getNotifications("db-p-1").stream().anyMatch(n->n.getNotificationId().equals(notificationId)),"persistent notification retained");
            }
        } finally {
            try(var files=Files.walk(directory)) {
                for(Path file:files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
    }

    private static Student patient(String id,String name) {
        Student patient=new Student(id,name,"contact","BSCS",2,"B");
        patient.setGuardianContact("guardian");
        patient.addAllergy("Penicillin");
        patient.addHistory("Existing history");
        return patient;
    }

    private static Student editedPatient(String id,String name) {
        Student patient=new Student(id,name,"updated contact","BSIT",3,"C");
        patient.setGuardianContact("updated guardian");
        return patient;
    }

    private static void check(boolean condition,String description) {
        if(!condition)throw new AssertionError(description);
        checks++;
    }

    private static void rejects(Class<? extends Throwable> type,Runnable work,String description) {
        try { work.run(); }
        catch(Throwable error) {
            if(type.isInstance(error)) { checks++; return; }
            throw new AssertionError(description+" (wrong exception: "+error+")",error);
        }
        throw new AssertionError(description+" (no exception)");
    }
}
