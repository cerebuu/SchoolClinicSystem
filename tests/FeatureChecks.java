import clinic.model.*;
import clinic.service.*;
import clinic.util.DateUtil;
import java.time.*;
import java.util.*;

/** Synthetic data only. Run: javac -cp out -d out tests/FeatureChecks.java; java -cp out FeatureChecks */
public final class FeatureChecks {
    private static int checks;
    private static final LocalDateTime NOW=LocalDateTime.now().minusSeconds(1);
    static void equal(String name,Object expected,Object actual) {
        System.out.println(name+" | expected="+expected+" | actual="+actual);
        if(!Objects.equals(expected,actual))throw new AssertionError(name);
        checks++;
    }
    static void rejects(String name,Class<? extends Throwable> type,Runnable action) {
        String actual="no exception";
        try {action.run();}catch(Exception e){actual=e.getClass().getSimpleName();}
        equal(name,type.getSimpleName(),actual);
    }
    static Student student(String id,String section) {
        Student p=new Student(id,"Private name "+id,"own-contact","CS",1,section);
        p.setGuardianContact("guardian-"+id);
        return p;
    }
    static MedicalRecord visit(String id,String patient,String symptom,String reason,int hoursAgo) {
        return new MedicalRecord(id,patient,List.of(symptom),reason,"private diagnosis","","n",NOW.minusHours(hoursAgo));
    }
    static Medicine batch(String name,int quantity,int expiresIn) {
        return new Medicine(name,quantity,LocalDate.now().plusDays(expiresIn),LocalDate.now().minusDays(10),"");
    }
    static InMemoryClinicSystem system(List<Patient> patients,List<MedicalRecord> records,
            List<MedicineTransaction> transactions,List<Medicine> batches) {
        return new InMemoryClinicSystem(new InMemoryClinicSystem.State(patients,records,transactions,List.of(),batches));
    }
    public static void main(String[] args) {
        Student p=student("p","A");
        List<MedicalRecord> three=List.of(visit("1","p","Fever","one",1),visit("2","p","FEVER","two",2),visit("3","p","fever","three",47));
        InMemoryClinicSystem clinic=system(List.of(p),three,List.of(),List.of(batch("Clean",30,100)));
        equal("Outbreak: 3 same-section visits in 48h",1,clinic.detectOutbreaks().size());
        equal("Outbreak: 2 visits",0,system(List.of(p),three.subList(0,2),List.of(),List.of()).detectOutbreaks().size());
        List<Patient> sections=List.of(student("a","A"),student("b","B"),student("c","C"));
        List<MedicalRecord> separate=List.of(visit("1","a","Fever","reason",1),visit("2","b","Fever","reason",1),visit("3","c","Fever","reason",1));
        equal("Outbreak: 3 different sections",0,system(sections,separate,List.of(),List.of()).detectOutbreaks().size());
        Employee employee=new Employee("p","Private employee","employee-own","A","Staff");
        equal("Outbreak: employee department",true,system(List.of(employee),three,List.of(),List.of()).detectOutbreaks().get(0).getMessage().contains("Employee department a"));
        equal("Outbreak: student and employee groups separate",0,system(List.of(student("a","A"),new Employee("b","Employee","own","A","Staff")),
            List.of(visit("1","a","Fever","r",1),visit("2","a","Fever","r",1),visit("3","b","Fever","r",1)),List.of(),List.of()).detectOutbreaks().size());
        equal("Pattern: null input",0,new PatternDetector().detectOutbreak(null,null,2,3).size());
        equal("Pattern: unknown patient",false,clinic.detectRecurringVisit("missing").isPresent());
        equal("Pattern: unknown symptom",0,system(List.of(p),List.of(visit("1","p","unknown","r",1),visit("2","p","unknown","r",1),visit("3","p","unknown","r",1)),List.of(),List.of()).detectOutbreaks().size());
        equal("Pattern: outside window/future excluded",0,system(List.of(p),List.of(three.get(0),visit("2","p","Fever","r",49),visit("3","p","Fever","r",-1)),List.of(),List.of()).detectOutbreaks().size());
        equal("Recurring: 3 same symptom in 14d",true,system(List.of(p),List.of(three.get(0),three.get(1),visit("3","p","Fever","different",13*24)),List.of(),List.of()).detectRecurringVisit("p").isPresent());
        equal("Recurring: same reason different symptoms",true,system(List.of(p),List.of(visit("1","p","Fever","shared",1),visit("2","p","Cough","SHARED",2),visit("3","p","Headache","shared",3)),List.of(),List.of()).detectRecurringVisit("p").isPresent());
        equal("Recurring: unrelated visits",false,system(List.of(p),List.of(visit("1","p","Fever","one",1),visit("2","p","Cough","two",2),visit("3","p","Headache","three",3)),List.of(),List.of()).detectRecurringVisit("p").isPresent());
        MedicineTransaction usage=new MedicineTransaction("t","Clean","p","n",60,LocalDate.now().plusDays(100),"",NOW.minusDays(10));
        InMemoryClinicSystem stock=system(List.of(p),List.of(),List.of(usage),List.of(batch("Clean",30,100),batch("Expired",10,-1),batch("Soon",4,2),batch("Later",5,9),batch("Empty",0,4)));
        equal("Forecast: 30 units / (60 / 30 days)",15.0,stock.estimateDaysRemaining("clean"));
        equal("Forecast: zero usage",Double.POSITIVE_INFINITY,stock.estimateDaysRemaining("Soon"));
        rejects("Forecast: unknown medicine",IllegalArgumentException.class,()->stock.estimateDaysRemaining("missing"));
        equal("Stock: expired excluded",0,stock.getStock("Expired"));
        equal("Expiry: usable batches sorted",List.of("Soon","Later"),stock.getExpiringWithin(30).stream().map(Medicine::getName).toList());
        equal("Expiry: expired separately reported",true,stock.getAllAlerts().stream().anyMatch(a->a.getType().equals("EXPIRED")));
        equal("Forecast: stock unchanged",30,stock.getStock("Clean"));
        equal("Low stock: threshold aggregated",true,stock.getLowStock().stream().allMatch(m->m.isLowStock(FeatureRules.LOW_STOCK_THRESHOLD)));
        p.addAllergy("Penicillin"); p.addAllergy("Direct"); p.addAllergy("NSAIDs"); p.addAllergy("Sulfa");
        InMemoryClinicSystem safe=system(List.of(p,employeeWithId()),List.of(),List.of(),List.of(batch("Direct",5,10),batch("Amoxicillin",5,10),batch("Clean",30,40),batch("Expired",8,-1),batch("Empty",0,10),batch("Ibuprofen",4,20),batch("Sulfamethoxazole",3,20)));
        equal("Recorded allergies require generic confirmation",List.of(SafetyChecker.ALLERGY_CONFIRMATION),safe.checkSafety("p","Direct"));
        equal("Safety: expired",true,safe.checkSafety("p","Expired").stream().anyMatch(s->s.contains("expired")));
        equal("Safety: out of stock",List.of(SafetyChecker.ALLERGY_CONFIRMATION,"Empty: out of stock"),safe.checkSafety("p","Empty"));
        equal("Safety: unknown patient",List.of("Patient not found."),safe.checkSafety("missing","Clean"));
        equal("Safety: unknown medicine",List.of(SafetyChecker.ALLERGY_CONFIRMATION,"Medicine not found: missing"),safe.checkSafety("p","missing"));
        equal("Every medicine requires staff review for recorded allergies",List.of(SafetyChecker.ALLERGY_CONFIRMATION),safe.checkSafety("p","Clean"));
        rejects("Dispense: allergy without override",IllegalStateException.class,()->safe.dispenseMedicine("p","Amoxicillin",1,"n",false));
        equal("Dispense: allergy override",1,safe.dispenseMedicine("p","Amoxicillin",1,"n",true).getQuantity());
        rejects("Dispense: expired even with override",IllegalStateException.class,()->safe.dispenseMedicine("p","Expired",1,"n",true));
        rejects("Dispense: out of stock even with override",IllegalStateException.class,()->safe.dispenseMedicine("p","Empty",1,"n",true));
        equal("Dispense: expired batch unchanged",8,safe.snapshot().batches().stream().filter(m->m.getName().equals("Expired")).findFirst().orElseThrow().getQuantity());
        MedicineTransaction tx=safe.dispenseMedicine("p","Clean",1,"n",true);
        NotificationLog first=safe.createGuardianNotification("p",tx.getTransactionId(),"private diagnosis; allergy; other patient");
        equal("Notification: created",NotificationStatus.PENDING,first.getStatus());
        equal("Notification: dedicated guardian contact","guardian-p",first.getRecipientContact());
        equal("Notification: free-text clinical details omitted",false,(first.toMessage()+first.getReason()).contains("private diagnosis"));
        safe.updateNotificationStatus(first.getNotificationId(),NotificationStatus.SENT);
        equal("Notification: sent",NotificationStatus.SENT,safe.getNotifications("p").get(0).getStatus());
        rejects("Notification: repeated SENT",IllegalArgumentException.class,()->safe.updateNotificationStatus(first.getNotificationId(),NotificationStatus.SENT));
        rejects("Notification: invalid ID",IllegalArgumentException.class,()->safe.updateNotificationStatus("missing",NotificationStatus.SENT));
        rejects("Notification: unknown patient",IllegalArgumentException.class,()->safe.createGuardianNotification("missing",tx.getTransactionId(),"reason"));
        NotificationLog second=safe.createGuardianNotification("p",tx.getTransactionId(),"reason");
        equal("Notification: newest first",second.getNotificationId(),safe.getNotifications("p").get(0).getNotificationId());
        second.markSent();
        equal("Notification: detached status",NotificationStatus.PENDING,safe.getNotifications("p").get(0).getStatus());
        for(String missing:Arrays.asList(null," ")) {
            Student noGuardian=student("none","A"); noGuardian.setGuardianContact(missing);
            InMemoryClinicSystem none=system(List.of(noGuardian),List.of(),List.of(),List.of(batch("Clean",5,20)));
            MedicineTransaction t=none.dispenseMedicine("none","Clean",1,"n",false);
            String message="no exception";
            try {none.createGuardianNotification("none",t.getTransactionId(),"reason");}
            catch(IllegalStateException e){message=e.getMessage();}
            equal("Notification: missing guardian ("+(missing==null?"null":"blank")+")","No guardian contact on file",message);
        }
        MedicineTransaction employeeTx=safe.dispenseMedicine("e","Clean",1,"n",false);
        NotificationLog employeeLog=safe.createGuardianNotification("e",employeeTx.getTransactionId(),"reason");
        equal("Notification: employee own contact",true,employeeLog.toMessage().startsWith("Employee contact (own): employee-own"));
        equal("Formatter: MedicalRecord",true,clinic.generateReport(three.get(0)).contains(DateUtil.format(three.get(0).getVisitDateTime())));
        equal("Formatter: MedicineTransaction",true,clinic.generateReport(tx).contains(DateUtil.format(tx.getDateTime())));
        Alert alert=clinic.detectOutbreaks().get(0);
        equal("Formatter: Alert",true,clinic.generateReport(alert).contains(DateUtil.format(alert.getCreatedAt())));
        equal("Formatter: arbitrary Formattable",true,clinic.generateReport(()->"custom").contains("custom"));
        rejects("Formatter: null",IllegalArgumentException.class,()->clinic.generateReport(null));
        InMemoryClinicSystem all=system(List.of(p),three,List.of(),List.of(batch("Soon",2,2)));
        List<Alert> alerts=all.getAllAlerts();
        equal("Alerts: all four feature types",Set.of("OUTBREAK","RECURRING_VISIT","LOW_STOCK","EXPIRING"),new HashSet<>(alerts.stream().map(Alert::getType).toList()));
        boolean ordered=true;
        for(int i=1;i<alerts.size();i++)ordered&=!alerts.get(i).getCreatedAt().isAfter(alerts.get(i-1).getCreatedAt());
        equal("Alerts: newest first",true,ordered);
        equal("Alerts: no patient names or clinical free text",false,alerts.toString().contains("Private")||alerts.toString().contains("private diagnosis"));
        equal("Patient: copy preserves guardian contact","guardian-p",p.copy().getGuardianContact());
        equal("Patient: old constructor defaults null",null,new Student("old","Old","own","CS",1,"A").getGuardianContact());
        System.out.println("PASS: "+checks+" feature checks.");
    }
    static Employee employeeWithId(){return new Employee("e","Private employee","employee-own","Office","Staff");}
}
