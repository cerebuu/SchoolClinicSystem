import clinic.app.*;
import clinic.model.*;
import clinic.persistence.*;
import clinic.service.*;
import clinic.util.AllergyParser;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** Parser, secured registration, safety and durable storage using synthetic patients only. */
public final class AllergyDemo {
    private static int checks;
    private static void check(String message, boolean ok) {
        if(!ok)throw new AssertionError(message);
        System.out.println("PASS: "+message); checks++;
    }
    private static void parse(String label,String input,List<String> expected) {
        try {
            List<String> result=AllergyParser.parse(input);
            System.out.println(label+" => "+result);
            check(label,Objects.equals(expected,result));
        } catch(IllegalArgumentException error) {
            System.out.println(label+" => rejected (no list returned): "+error.getMessage());
            check(label,expected==null);
        }
    }
    public static void main(String[] args)throws Exception {
        parse("Penicillin, Aspirin","Penicillin, Aspirin",List.of("Penicillin","Aspirin"));
        parse("  penicillin ,PENICILLIN,, Aspirin ","  penicillin ,PENICILLIN,, Aspirin ",List.of("penicillin","Aspirin"));
        parse("empty string","",List.of()); parse("null",null,List.of());
        parse("21 items",String.join(",",java.util.stream.IntStream.range(0,21).mapToObj(i->"Item"+i).toList()),null);
        parse("51-character item","x".repeat(51),null);
        parse("Peanuts;Latex","Peanuts;Latex",List.of("Peanuts","Latex"));
        parse("newlines and repeated spaces","  Tree   nuts\nLatex\r\nTree nuts",List.of("Tree nuts","Latex"));
        AuditLog audit=new AuditLog();
        try(ApplicationContext app=new ApplicationContext(true,audit)) {
            app.login.login("N001","nurse123").orElseThrow();
            Patient student=new Student("s","Synthetic student","contact","CS",1,"A");
            AllergyParser.parse("Penicillin, Aspirin").forEach(student::addAllergy);
            app.clinic().registerPatient(student);
            System.out.println("Stored student allergies: "+app.clinic().findPatient("s").orElseThrow().getAllergies());
            for(String name:List.of("amoxicillin","aspirin","paracetamol")) {
                app.clinic().addMedicine(name,10,LocalDate.now().plusDays(30),"");
                List<String> warnings=app.clinic().checkSafety("s",name);
                System.out.println("checkSafety("+name+") => "+warnings);
                check("Safety "+name,warnings.equals(List.of(SafetyChecker.ALLERGY_CONFIRMATION)));
            }
            check("Confirmation independent of medicine name",!app.clinic().checkSafety("s","AMOXICILLIN").isEmpty());
            Patient employee=new Employee("e","Synthetic employee","contact","Office","Staff");
            AllergyParser.parse(null).forEach(employee::addAllergy); app.clinic().registerPatient(employee);
            System.out.println("Stored employee allergies: "+app.clinic().findPatient("e").orElseThrow().getAllergies());
            check("Employee has empty list",app.clinic().findPatient("e").orElseThrow().getAllergies().isEmpty());
            app.clinic().addAllergy("s","Latex");
            for(var entry:audit.getAll())if(entry.getAction().equals("registerPatient")||entry.getAction().equals("addAllergy")) {
                System.out.println("Audit: user="+entry.getUserId()+", action="+entry.getAction()+", details="+entry.getDetails());
                check("Count-only audit details",entry.getDetails().matches("Allergy count: [0-9]+"));
            }
        }
        Path path=Files.createTempDirectory("scms-allergy-demo-").resolve("clinic");
        try(Database db=new Database(path)) {
            ClinicData data=ClinicRepository.create(db);
            SchoolNurse nurse=new SchoolNurse("n","Fixture nurse");nurse.setCampusId("C1");
            var secured=new SecuredClinicSystem(data,nurse,new AuditLog());
            Patient p=new Student("s","Synthetic student","contact","CS",1,"A");
            AllergyParser.parse("Penicillin, Aspirin").forEach(p::addAllergy); secured.registerPatient(p);
            secured.registerPatient(new Employee("e","Synthetic employee","contact","Office","Staff"));
        }
        try(Database db=new Database(path)) {
            ClinicData data=ClinicRepository.create(db);
            var allergies=data.findPatient("s").orElseThrow().getAllergies();
            System.out.println("Reopened student allergies: "+allergies);
            check("Individual allergies persist",new HashSet<>(allergies).equals(Set.of("Penicillin","Aspirin")));
            check("No allergy rows loads empty",data.findPatient("e").orElseThrow().getAllergies().isEmpty());
            check("Persisted allergies require staff review",!data.findPatient("s").orElseThrow().getAllergies().isEmpty());
        }
        System.out.println("PASS: "+checks+" allergy parser, safety, audit and persistence checks.");
    }
}
