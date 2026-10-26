import clinic.model.Medicine;
import clinic.persistence.*;
import clinic.service.ClinicData;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

/** Persistence and rollback demonstration for inventory batch merging. */
public final class BatchPersistenceDemo {
    private static void print(String label,ClinicData clinic){
        System.out.println(label+": "+clinic.medicinePage(0,100,"").rows().stream()
            .map(m->m.getName()+" qty="+m.getQuantity()+" expiry="+m.getExpirationDate()+" batch="+m.getBatchDate()+" remarks=["+m.getRemarks()+"]").toList());
    }
    public static void main(String[] args)throws Exception{
        var file=Files.createTempDirectory("scms-batch-merge-").resolve("clinic");
        LocalDate expiry=LocalDate.of(2049,12,13);
        try(Database database=new Database(file)){
            ClinicData clinic=ClinicRepository.create(database);
            clinic.addMedicine("paracetamol",12,expiry,"original");
            print("Persistent before",clinic);
            clinic.mergeMedicineBatch("PARACETAMOL",12,expiry,"new note");
            print("Persistent after merge",clinic);
            clinic.restockMedicine("paracetamol",3,expiry,"duplicate row");
            clinic.mergeDuplicateMedicineBatches("paracetamol",expiry);
            print("Persistent after cleanup merge",clinic);
            database.transaction(c->{try(var s=c.createStatement()){s.execute("ALTER TABLE medicine_batches ADD CONSTRAINT batch_merge_failure CHECK(quantity<28)");}return null;});
            try{clinic.mergeMedicineBatch("paracetamol",2,expiry,"must rollback");throw new AssertionError("constraint failure expected");}
            catch(StorageException expected){System.out.println("PASS failed database update reported; transaction rolled back");}
            print("After failed merge",clinic);
        }
        try(Database reopened=new Database(file)){
            ClinicData clinic=ClinicRepository.create(reopened);
            List<Medicine> rows=clinic.medicinePage(0,100,"").rows();
            System.out.println("After restart: "+rows.stream().map(m->m.getName()+" qty="+m.getQuantity()+" remarks=["+m.getRemarks()+"]").toList());
            if(rows.size()!=1||rows.get(0).getQuantity()!=27||!rows.get(0).getRemarks().contains("new note")||!rows.get(0).getRemarks().contains("duplicate row"))throw new AssertionError("merged batch did not persist");
            System.out.println("PASS merged quantity and remarks survive restart; failed merge left no partial update");
        }
    }
}
