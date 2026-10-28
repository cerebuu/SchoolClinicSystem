import clinic.app.ApplicationContext;
import clinic.core.ClinicSystem;
import clinic.model.Campus;
import clinic.model.MedicationHistoryEntry;
import clinic.model.SchoolNurse;
import clinic.model.Student;
import clinic.service.DispenseItem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Verifies simultaneous dispense requests cannot deduct the same stock twice. */
public final class ConcurrentDispenseChecks {
    public static void main(String[] args) throws Exception {
        Path folder=Files.createTempDirectory("scms-concurrent-dispense-");
        try {
            try(ApplicationContext app=new ApplicationContext(folder.resolve("clinic"))) {
                app.login.bootstrapAdministrator("root","Admin","admin-secret");
                app.login.login("root","admin-secret").orElseThrow();
                app.admin.saveCampus(new Campus("C1","Campus 1"),false);
                SchoolNurse nurse=new SchoolNurse("N1","Nurse");
                nurse.setCampusId("uic-bajada");
                app.admin.addUser(nurse,"nurse-secret");
                app.login.logout();
                app.login.login("N1","nurse-secret").orElseThrow();

                ClinicSystem clinic=app.clinic();
                clinic.registerPatient(new Student("P1","Patient","contact","BSCS",1,"A"));
                clinic.addMedicine("Concurrency medicine",2,LocalDate.now().plusDays(60),"");

                CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
                ExecutorService pool=Executors.newFixedThreadPool(2);
                try {
                    Future<Boolean> first=pool.submit(()->dispense(clinic,ready,start,false));
                    Future<Boolean> second=pool.submit(()->dispense(clinic,ready,start,false));
                    ready.await();
                    start.countDown();
                    int successes=(first.get()?1:0)+(second.get()?1:0);
                    check(successes==1,"only one simultaneous request can consume the two available units");
                } finally {
                    pool.shutdownNow();
                }

                check(clinic.getStock("Concurrency medicine")==0,"concurrent requests never create negative stock");
                List<MedicationHistoryEntry> entries=app.medicationHistory(0,25,"",null,null).rows();
                check(entries.stream().filter(entry->entry.type()==MedicationHistoryEntry.Type.DISPENSE).count()==1,
                    "the successful stock deduction creates one administration record");
                check(entries.stream().filter(entry->entry.type()==MedicationHistoryEntry.Type.INITIAL_STOCK).count()==1,
                    "initial stock remains a separate history event");

                clinic.restockMedicine("Concurrency medicine",2,LocalDate.now().plusDays(60));
                CountDownLatch consultationsReady=new CountDownLatch(2),consultationsStart=new CountDownLatch(1);
                ExecutorService consultations=Executors.newFixedThreadPool(2);
                try {
                    Future<Boolean> first=consultations.submit(()->dispense(clinic,consultationsReady,consultationsStart,true));
                    Future<Boolean> second=consultations.submit(()->dispense(clinic,consultationsReady,consultationsStart,true));
                    consultationsReady.await();
                    consultationsStart.countDown();
                    check((first.get()?1:0)+(second.get()?1:0)==1,
                        "only one concurrent consultation can administer the remaining stock");
                } finally {
                    consultations.shutdownNow();
                }
                check(clinic.getStock("Concurrency medicine")==0,"concurrent consultations cannot create negative stock");
                check(clinic.getVisitHistory("P1").size()==1,"rejected consultation leaves no partial visit");
                check(app.medicationHistory(0,25,"",null,null).rows().stream()
                        .filter(entry->entry.type()==MedicationHistoryEntry.Type.DISPENSE&&entry.visitId()!=null).count()==1,
                    "successful consultation has exactly one linked administration");
            }
        } finally {
            try(var paths=Files.walk(folder)) {
                for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
            }
        }
        System.out.println("PASS: 8 concurrent dispensing and consultation integrity checks.");
    }

    private static boolean dispense(ClinicSystem clinic,CountDownLatch ready,CountDownLatch start,
            boolean consultation) throws Exception {
        ready.countDown();
        start.await();
        try {
            if(consultation)
                clinic.recordVisitAndAdminister("P1",List.of("Cough"),"Concurrent consultation","","N1",
                    List.of(new DispenseItem("Concurrency medicine",2)),false);
            else clinic.dispenseMedicine("P1","Concurrency medicine",2,"N1",false);
            return true;
        } catch(IllegalStateException rejected) {
            return false;
        }
    }

    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
}
