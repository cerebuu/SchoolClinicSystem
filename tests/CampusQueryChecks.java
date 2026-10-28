import clinic.app.ApplicationContext;
import clinic.model.*;
import clinic.persistence.ClinicRepository;
import clinic.persistence.Database;
import clinic.service.ClinicData;
import clinic.service.DispenseItem;
import clinic.service.InMemoryClinicSystem;
import clinic.service.PageResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Campus filtering checks for clinical search, analytics, counts, and dispense paging. */
public final class CampusQueryChecks {
    private static int checks;
    private static final MedicineProduct MEDICINE=new MedicineProduct("Loratadine","R06AX13","");

    public static void main(String[] args) throws Exception {
        memoryQueriesRespectCampus();
        databaseQueriesRespectCampus();
        System.out.println("Campus query checks passed: "+checks);
    }

    private static void memoryQueriesRespectCampus() {
        InMemoryClinicSystem data=new InMemoryClinicSystem();
        populate(data);
        check(data.recordCount("uic-bajada")==5,"memory campus A visit count excludes B and legacy NULL");
        check(data.recordCount("uic-annex")==2,"memory campus B visit count excludes A and legacy NULL");
        check(data.recordCount()==8,"unscoped fixture visit count remains global");
        check(data.transactionPage(0,1,"",null,null,"uic-bajada").total()==2,"memory transaction count is campus filtered");
        check(data.transactionPage(1,1,"",null,null,"uic-bajada").rows().size()==1,"memory transaction page is bounded");
        rejects(IllegalArgumentException.class,()->data.transactionPage(-1,1,"",null,null,"uic-bajada"),
            "memory negative transaction page is rejected");
        check(data.transactionPage(2,1,"",null,null,"uic-annex").rows().isEmpty(),"memory out-of-range campus page is empty");
        check(data.transactionPage(0,1000,"LORATADINE",null,null,"uic-bajada").size()==100,
            "memory transaction page size is capped");
        check(data.transactionPage(0,25,"",LocalDateTime.now().plusDays(1),null,"uic-bajada").total()==0,
            "memory transaction date filter precedes count");
        check(data.transactionPage(0,25,"",null,null,null).total()==5,"unscoped fixture transaction page remains global");
        check(data.searchRecords("","Fever","",null,"G-1","","uic-bajada").size()==3,
            "memory record search returns same-campus section matches");
        check(data.searchRecords("","Fever","",null,"G-1","","uic-annex").size()==1,
            "memory record search excludes another campus");
        check(data.searchRecords("","Fever","",null,"G-1","",null).size()==5,
            "unscoped fixture search remains global and includes NULL campus");
        check(data.searchRecords("repeat-p","","campus b only",LocalDate.now(),"","","uic-bajada").isEmpty(),
            "memory medicine search cannot match a different campus dispense");
        check(data.searchRecords("repeat-p","","campus b only",LocalDate.now(),"","","uic-annex").size()==1,
            "memory medicine search matches same-campus dispense");
        check(data.detectOutbreaks("uic-bajada").size()==1,"memory outbreak uses campus A visits only");
        check(data.detectOutbreaks("uic-annex").isEmpty(),"memory outbreak excludes other campus and NULL visit");
        check(data.detectRecurringVisit("repeat-p","uic-bajada").isEmpty(),"memory recurring alert excludes campus B visit");
        check(data.detectRecurringVisit("repeat-p","uic-annex").isEmpty(),"memory recurring alert excludes campus A visits");
        check(data.detectRecurringVisit("repeat-p",null).isPresent(),"unscoped fixture recurring alert remains global");
        check(data.alertPage(0,25,"","uic-bajada").total()==1,"memory alert page filters clinical inputs by campus");
        check(data.alertPage(0,25,"","uic-annex").total()==0,"memory alert page excludes cross-campus clinical alert");
        check(data.getAllAlerts("uic-bajada").stream().noneMatch(a->a.getMessage().contains("repeat-p")),
            "memory campus alert details exclude cross-campus recurring patient");
    }

    private static void databaseQueriesRespectCampus() throws Exception {
        Path folder=Files.createTempDirectory("scms-campus-query-");
        Path file=folder.resolve("clinic");
        try {
            try(ApplicationContext app=new ApplicationContext(file)) {
                app.login.bootstrapAdministrator("root","Administrator","root-secret");
                app.login.login("root","root-secret").orElseThrow();
                SchoolNurse a=new SchoolNurse("n-a","Nurse A");a.setCampusId("uic-bajada");
                SchoolNurse b=new SchoolNurse("n-b","Nurse B");b.setCampusId("uic-annex");
                app.admin.addUser(a,"nurse-a-secret");
                app.admin.addUser(b,"nurse-b-secret");
            }
            try(Database database=new Database(file)) {
                ClinicData data=ClinicRepository.create(database);
                populate(data);
                check(data.recordCount("uic-bajada")==5,"SQL campus A visit count uses exact campus filter");
                check(data.recordCount("uic-annex")==2,"SQL campus B visit count excludes other campuses");
                check(data.recordCount("unknown-campus")==0,"SQL unknown campus count is empty");
                check(data.transactionPage(0,1,"",null,null,"uic-bajada").total()==2,
                    "SQL transaction count is filtered before paging");
                check(data.transactionPage(1,1,"",null,null,"uic-bajada").rows().size()==1,
                    "SQL campus transaction second page has one row");
                rejects(IllegalArgumentException.class,()->data.transactionPage(-1,1,"",null,null,"uic-bajada"),
                    "SQL negative transaction page is rejected");
                check(data.transactionPage(0,25,"",null,null,"uic-annex").total()==2,
                    "SQL campus B transaction count excludes A and NULL");
                check(data.transactionPage(4,1,"",null,null,"uic-bajada").rows().isEmpty(),
                    "SQL out-of-range campus page is empty");
                check(data.transactionPage(0,1000,"",null,null,"uic-bajada").size()==100,
                    "SQL transaction page size is capped");
                check(data.transactionPage(0,25,"LORATADINE",LocalDateTime.now().minusDays(1),null,"uic-bajada").total()==2,
                    "SQL search and date filters apply within campus");
                check(data.searchRecords("","Fever","",null,"G-1","","uic-bajada").size()==3,
                    "SQL record search filters by campus before return");
                check(data.searchRecords("","Fever","",null,"G-1","","uic-annex").size()==1,
                    "SQL record search excludes another campus");
                check(data.searchRecords("","Fever","",null,"G-1","",null).size()==5,
                    "SQL unscoped search retains legacy/global fixture behavior");
                check(data.searchRecords("repeat-p","","campus b only",LocalDate.now(),"","","uic-bajada").isEmpty(),
                    "SQL medicine search cannot match a different campus dispense");
                check(data.searchRecords("repeat-p","","campus b only",LocalDate.now(),"","","uic-annex").size()==1,
                    "SQL medicine search matches same-campus dispense");
                check(data.detectOutbreaks("uic-bajada").size()==1&&data.detectOutbreaks("uic-annex").isEmpty(),
                    "SQL-backed outbreak analytics use scoped visits");
                check(data.detectRecurringVisit("repeat-p","uic-bajada").isEmpty()
                    &&data.detectRecurringVisit("repeat-p",null).isPresent(),
                    "SQL-backed recurring analytics use scoped visits");
                check(data.alertPage(0,25,"","uic-bajada").total()==1
                    &&data.alertPage(0,25,"","uic-annex").total()==0,
                    "SQL-backed alert pages filter clinical analytics by campus");
            }
            try(ApplicationContext a=new ApplicationContext(file)) {
                a.login.login("n-a","nurse-a-secret").orElseThrow();
                PageResult<MedicineTransaction> transactionsA=a.transactions(0,1,"",null,null);
                check(transactionsA.total()==2&&transactionsA.rows().size()==1,
                    "ApplicationContext transactions use authenticated campus");
                check(a.transactions(1,1,"",null,null).rows().size()==1,"application transaction page stays bounded");
                check(a.visitCount()==5,"ApplicationContext visit count uses authenticated campus");
                check(a.alerts(0,25,"").total()==1,"ApplicationContext alert page uses authenticated campus");
            }
            try(ApplicationContext b=new ApplicationContext(file)) {
                b.login.login("n-b","nurse-b-secret").orElseThrow();
                check(b.transactions(0,25,"",null,null).total()==2,"campus B application transaction page is isolated");
                check(b.visitCount()==2,"campus B application visit count is isolated");
                check(b.alerts(0,25,"").total()==0,"campus B application alert page excludes A analytics");
            }
        } finally {
            try(var paths=Files.walk(folder)) {
                for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
            }
        }
    }

    private static void populate(ClinicData data) {
        for(int i=1;i<=3;i++) {
            register(data,"a-fever-"+i,"A fever "+i,"G-1");
            data.recordVisitWithCampus("a-fever-"+i,List.of("Fever"),"Campus A visit","","n-a","uic-bajada");
        }
        register(data,"b-fever-1","B fever","G-1");
        data.recordVisitWithCampus("b-fever-1",List.of("Fever"),"Campus B visit","","n-b","uic-annex");
        register(data,"legacy-fever","Legacy fever","G-1");
        data.recordVisit("legacy-fever",List.of("Fever"),"Legacy visit","","n-a");
        register(data,"repeat-p","Repeat patient","G-2");
        data.recordVisitWithCampus("repeat-p",List.of("Cough"),"Repeat concern","","n-a","uic-bajada");
        data.recordVisitWithCampus("repeat-p",List.of("Cough"),"Repeat concern","","n-a","uic-bajada");
        data.addMedicine(MEDICINE,30,LocalDate.now().plusDays(90),"");
        MedicineProduct campusBOnly=new MedicineProduct("Campus B only","","");
        data.addMedicine(campusBOnly,30,LocalDate.now().plusDays(90),"");
        data.dispenseMedicineWithCampus("a-fever-1",MEDICINE,1,"n-a","","uic-bajada");
        data.dispenseMedicineWithCampus("a-fever-2",MEDICINE,1,"n-a","","uic-bajada");
        data.dispenseMedicineWithCampus("b-fever-1",MEDICINE,1,"n-b","","uic-annex");
        data.dispenseMedicine("legacy-fever",MEDICINE,1,"n-a",false);
        data.recordVisitAndAdministerWithCampus("repeat-p",List.of("Cough"),"Repeat concern","","n-b",
            List.of(new DispenseItem(campusBOnly,1)),false,"uic-annex");
    }

    private static void register(ClinicData data,String id,String name,String section) {
        data.registerPatient(new Student(id,name,"contact","BSCS",2,section));
    }

    private static void check(boolean condition,String description) {
        if(!condition)throw new AssertionError(description);
        checks++;
    }

    private static void rejects(Class<? extends Throwable> type,Runnable work,String description) {
        try { work.run(); }
        catch(Throwable error) {
            if(type.isInstance(error)){checks++;return;}
            throw new AssertionError(description+" (wrong exception: "+error+")",error);
        }
        throw new AssertionError(description+" (no exception)");
    }
}
