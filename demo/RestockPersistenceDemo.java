import clinic.model.*;
import clinic.persistence.*;
import clinic.service.*;
import java.nio.file.*;
import java.time.LocalDate;

/** Durable batch, legacy migration, validation and permission checks on a disposable database. */
public final class RestockPersistenceDemo {
    private static int checks;
    private static void check(String message, boolean condition) {
        System.out.println((condition?"PASS: ":"FAIL: ")+message);
        if(!condition)throw new AssertionError(message);
        checks++;
    }
    private static void rejected(String label,Runnable work) {
        try { work.run(); throw new AssertionError(label); }
        catch(IllegalArgumentException | SecurityException expected) {
            System.out.println("PASS: "+label+" blocked: "+expected.getMessage()); checks++;
        }
    }
    public static void main(String[] args)throws Exception {
        Path path=Files.createTempDirectory("scms-restock-demo-").resolve("clinic");
        LocalDate future=LocalDate.now().plusDays(30);
        try(Database db=new Database(path)) {
            ClinicData data=ClinicRepository.create(db);
            data.addMedicine("Legacy medicine",1,future,"");
            db.transaction(c -> { try(var s=c.createStatement()) {
                s.execute("ALTER TABLE medicine_batches DROP COLUMN remarks");
                s.executeUpdate("UPDATE schema_version SET version=3");
            }return null; });
        }
        try(Database db=new Database(path)) {
            ClinicData data=ClinicRepository.create(db);
            check("Missing legacy remarks column migrates to empty",data.medicinePage(0,25,"").rows().get(0).getRemarks().equals(""));
            AuditLog audit=new AuditLog(new AdminRepository(db));
            SchoolNurse nurse=new SchoolNurse("N-DEMO","Demo nurse");nurse.setCampusId("uic-bajada");
            SecuredClinicSystem secured=new SecuredClinicSystem(data,nurse,audit);
            secured.restockMedicine("Legacy medicine",2,future,"  delivery checked  ");
            secured.restockMedicine("Legacy medicine",3,future,null);
            rejected("201-character domain input",() -> secured.restockMedicine("Legacy medicine",1,future,"x".repeat(201)));
            rejected("Zero quantity",() -> secured.restockMedicine("Legacy medicine",0,future,""));
            rejected("Past expiry",() -> secured.restockMedicine("Legacy medicine",1,LocalDate.now().minusDays(1),""));
            rejected("Today expiry",() -> secured.restockMedicine("Legacy medicine",1,LocalDate.now(),""));
            rejected("Unknown medicine",() -> secured.restockMedicine("Unknown",1,future,""));
            rejected("Doctor lacks restock permission",() -> new SecuredClinicSystem(data,
                new Doctor("D-DEMO","Demo doctor","General"),audit).restockMedicine("Legacy medicine",1,future,"private"));
            check("Rejected operations create no batches",data.medicinePage(0,25,"").total()==3);
            check("Audit excludes remarks",audit.getAll().stream().allMatch(e -> e.getDetails().equals("Legacy medicine")));
        }
        try(Database db=new Database(path)) {
            var batches=ClinicRepository.create(db).medicinePage(0,25,"").rows();
            check("Reopen retains trimmed batch remarks",batches.get(1).getRemarks().equals("delivery checked"));
            check("Reopen retains empty optional remarks",batches.get(2).getRemarks().equals(""));
            for(Medicine batch:batches)System.out.println("Persisted batch: "+batch.getName()+", quantity="
                +batch.getQuantity()+", expiry="+batch.getExpirationDate()+", batchDate="+batch.getBatchDate()
                +", remarks=\""+batch.getRemarks()+"\"");
            // Older nullable columns also load safely and are normalized by the migration.
            db.transaction(c -> {try(var s=c.createStatement()) {
                s.execute("ALTER TABLE medicine_batches ALTER COLUMN remarks DROP NOT NULL");
                s.executeUpdate("UPDATE medicine_batches SET remarks=NULL WHERE ordinal=0");
                s.executeUpdate("UPDATE schema_version SET version=3");
            }return null;});
        }
        try(Database db=new Database(path)) {
            check("Legacy null remarks load as empty",ClinicRepository.create(db).medicinePage(0,25,"").rows().get(0).getRemarks().equals(""));
        }
        System.out.println("PASS: "+checks+" restock persistence checks.");
    }
}
