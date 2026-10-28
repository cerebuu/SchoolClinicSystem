import clinic.core.ClinicSystem;
import clinic.model.*;
import clinic.service.*;
import java.time.LocalDate;
import java.util.List;

/** Success-only audit assertions for clinical mutations. */
public final class ClinicalAuditChecks {
    private static int checks;

    public static void main(String[] args) {
        InMemoryClinicSystem store=new InMemoryClinicSystem();
        AuditLog audit=new AuditLog();
        SchoolNurse nurse=new SchoolNurse("nurse-actor","Nurse Actor");
        nurse.setCampusId("uic-bajada");
        ClinicSystem clinic=new SecuredClinicSystem(store,nurse,audit);
        Student patient=new Student("private-patient-id","Private Patient Name","contact","Course",1,"Section");
        patient.setGuardianContact("guardian@example.test");
        store.registerPatient(patient);

        String symptom="private-symptom-value";
        String reason="private-reason-value";
        String assessment="private-assessment-value";
        MedicalRecord visit=clinic.recordVisit(patient.getId(),List.of(symptom),reason,assessment,nurse.getUserId());
        List<AuditEntry> visitEvents=events(audit,"recordVisit",true);
        check("successful visit emits exactly one success event",1,visitEvents.size());
        check("visit success identifies its record and symptom count",
            true,visitEvents.get(0).getDetails().contains("recordId="+visit.getRecordId())
                &&visitEvents.get(0).getDetails().contains("symptomCount=1"));
        check("visit success carries trusted campus","uic-bajada",visitEvents.get(0).getCampusId());
        privacy("visit success audit",visitEvents.get(0),patient,symptom,reason,assessment);

        rejects(IllegalArgumentException.class,()->clinic.recordVisit("missing-patient-id",List.of("secret"),
            "secret reason","secret assessment",nurse.getUserId()),"failed visit is rejected");
        check("failed visit adds no completed mutation event",1,events(audit,"recordVisit",true).size());

        MedicineProduct product=new MedicineProduct("private-medicine-name","private-strength","private-formulation");
        store.addMedicine(product,8,LocalDate.now().plusDays(30),"private-inventory-remarks");
        MedicineTransaction transaction=clinic.dispenseMedicine(patient.getId(),product,2,nurse.getUserId(),false);
        List<AuditEntry> dispenseEvents=events(audit,"dispenseMedicine",true);
        check("successful dispense emits exactly one success event",1,dispenseEvents.size());
        check("dispense success identifies transaction and quantity",true,
            dispenseEvents.get(0).getDetails().contains("transactionId="+transaction.getTransactionId())
                &&dispenseEvents.get(0).getDetails().contains("quantity=2"));
        check("dispense success carries trusted campus","uic-bajada",dispenseEvents.get(0).getCampusId());
        privacy("dispense success audit",dispenseEvents.get(0),patient,product.name(),product.strength(),product.formulation());

        rejects(IllegalArgumentException.class,()->clinic.dispenseMedicine(patient.getId(),product,100,
            nurse.getUserId(),false),"insufficient-stock dispense is rejected");
        check("failed dispense adds no completed mutation event",1,events(audit,"dispenseMedicine",true).size());

        MedicineProduct administered=new MedicineProduct("private-administered-medicine","dose","tablet");
        store.addMedicine(administered,4,LocalDate.now().plusDays(30),"");
        MedicalRecord administeredVisit=clinic.recordVisitAndAdminister(patient.getId(),List.of("private-combined-symptom"),
            "private-combined-reason","private-combined-assessment",nurse.getUserId(),
            List.of(new DispenseItem(administered,1)),false);
        List<AuditEntry> composite=events(audit,"recordVisitAndAdminister",true);
        check("successful combined workflow emits one completion event",1,composite.size());
        check("combined workflow identifies visit and medication count",true,
            composite.get(0).getDetails().contains("recordId="+administeredVisit.getRecordId())
                &&composite.get(0).getDetails().contains("medicationCount=1"));
        check("combined workflow carries trusted campus","uic-bajada",composite.get(0).getCampusId());
        privacy("combined workflow audit",composite.get(0),patient,"private-combined-symptom",
            "private-combined-reason","private-combined-assessment",administered.name());

        SchoolNurse c2User=new SchoolNurse("nurse-c2","Campus Two Nurse");
        c2User.setCampusId("uic-annex");
        ClinicSystem c2=new SecuredClinicSystem(store,c2User,new AuditLog());
        for(int i=0;i<3;i++)c2.recordVisit(patient.getId(),List.of("Fever"),"Concern","",c2User.getUserId());
        check("search records only returns current campus",0,
            clinic.searchRecords(patient.getId(),"Fever",null,null,null,null).size());
        check("C2 search returns C2 visits",3,
            c2.searchRecords(patient.getId(),"Fever",null,null,null,null).size());
        check("recurring-visit detection excludes other campus visits",true,
            clinic.detectRecurringVisit(patient.getId()).isEmpty());
        check("recurring-visit detection sees current campus visits",true,
            c2.detectRecurringVisit(patient.getId()).isPresent());
        check("outbreak detection excludes other campus visits",true,
            clinic.detectOutbreaks().stream().noneMatch(alert->alert.getType().equals("OUTBREAK")));
        check("outbreak detection counts current campus visits",true,
            c2.detectOutbreaks().stream().anyMatch(alert->alert.getType().equals("OUTBREAK")));
        List<Alert> c1Alerts=clinic.getAllAlerts(),c2Alerts=c2.getAllAlerts();
        check("aggregate alerts scope clinical visits to campus",true,
            c1Alerts.stream().noneMatch(alert->alert.getType().equals("OUTBREAK")
                ||alert.getType().equals("RECURRING_VISIT"))
                &&c2Alerts.stream().anyMatch(alert->alert.getType().equals("OUTBREAK"))
                &&c2Alerts.stream().anyMatch(alert->alert.getType().equals("RECURRING_VISIT")));
        check("aggregate alerts retain shared inventory warnings",true,
            c1Alerts.stream().anyMatch(alert->alert.getType().equals("LOW_STOCK"))
                &&c2Alerts.stream().anyMatch(alert->alert.getType().equals("LOW_STOCK")));

        int visitsBefore=store.recordPage(0,25,"",null).total();
        int transactionsBefore=store.transactionPage(0,25,"",null,null).total();
        rejects(IllegalStateException.class,()->clinic.recordVisitAndAdminister(patient.getId(),List.of("secret"),
            "secret reason","secret assessment",nurse.getUserId(),List.of(new DispenseItem(administered,99)),false),
            "failed combined workflow is rejected");
        check("failed combined workflow records no visit",visitsBefore,store.recordPage(0,25,"",null).total());
        check("failed combined workflow records no dispense",transactionsBefore,
            store.transactionPage(0,25,"",null,null).total());
        check("failed combined workflow adds no completion event",1,events(audit,"recordVisitAndAdminister",true).size());

        Doctor doctor=new Doctor("doctor-actor","Doctor Actor","");
        doctor.setCampusId("uic-bajada");
        ClinicSystem deniedClinic=new SecuredClinicSystem(store,doctor,audit);
        rejects(SecurityException.class,()->deniedClinic.dispenseMedicine(patient.getId(),product,1,
            doctor.getUserId(),false),"dispense permission denial remains enforced");
        check("permission denial remains auditable",1,events(audit,"dispenseMedicine",false).size());

        System.out.println("PASS: "+checks+" clinical audit checks.");
    }

    private static List<AuditEntry> events(AuditLog audit,String action,boolean allowed) {
        return audit.getAll().stream().filter(entry->entry.getAction().equals(action)
            &&entry.isAllowed()==allowed).toList();
    }

    private static void privacy(String label,AuditEntry event,Object... privateValues) {
        String text=event.getDetails()+" "+event.getAction();
        for(Object value:privateValues) {
            if(value==null)continue;
            String candidate=value.toString();
            if(candidate.length()>4&&text.contains(candidate))
                throw new AssertionError(label+" contains private value "+candidate);
        }
        checks++;
        System.out.println("PASS: "+label+" excludes patient identifiers and clinical free text");
    }

    private static void check(String description,Object expected,Object actual) {
        if(!java.util.Objects.equals(expected,actual))
            throw new AssertionError(description+"; expected="+expected+", actual="+actual);
        checks++;
        System.out.println("PASS: "+description);
    }

    private static void rejects(Class<? extends Throwable> type,Runnable action,String description) {
        try { action.run(); }
        catch(Throwable error) {
            if(!type.isInstance(error))throw new AssertionError(description+"; unexpected "+error.getClass().getSimpleName(),error);
            checks++;
            System.out.println("PASS: "+description);
            return;
        }
        throw new AssertionError(description+"; expected "+type.getSimpleName());
    }
}
