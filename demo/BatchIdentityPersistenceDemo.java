import clinic.model.Medicine;
import clinic.persistence.*;
import clinic.service.ClinicData;
import java.nio.file.Files;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Durable UUID, refill, migration, and rollback verification. */
public final class BatchIdentityPersistenceDemo {
    private static void seed(Database database,int ordinal,String name,LocalDate expiry,LocalDate batch,String remarks) {
        database.transaction(c->{try(PreparedStatement p=c.prepareStatement("INSERT INTO medicine_batches VALUES(?,?,?,?,?,?)")){
            p.setInt(1,ordinal);p.setString(2,name);p.setInt(3,0);p.setObject(4,expiry);p.setObject(5,batch);p.setString(6,remarks);p.executeUpdate();
        }return null;});
    }
    private static void print(String title,ClinicData clinic,String name){
        System.out.println(title+": "+clinic.medicinePage(0,100,name).rows().stream().map(m->m.getBatchId()+" "+m.getName()+" qty="+m.getQuantity()+" expiry="+m.getExpirationDate()+" batch="+m.getBatchDate()+" remarks=["+m.getRemarks()+"]").toList());
    }
    private static void expect(Class<? extends Throwable> type,Runnable action,String label){try{action.run();throw new AssertionError(label+" did not fail");}catch(Throwable error){if(!type.isInstance(error))throw new AssertionError(label,error);System.out.println("PASS "+label+": "+error.getClass().getSimpleName());}}
    public static void main(String[] args)throws Exception{
        var file=Files.createTempDirectory("scms-batch-id-").resolve("clinic");
        LocalDate oldExpiry=LocalDate.of(2049,12,13),newExpiry=LocalDate.of(2051,12,13);
        String stableId;
        try(Database database=new Database(file)){
            ClinicData clinic=ClinicRepository.create(database);
            seed(database,0,"refill",oldExpiry,LocalDate.now().minusDays(8),"old note");
            Medicine empty=clinic.medicinePage(0,10,"refill").rows().get(0);stableId=empty.getBatchId();
            System.out.println("Created batch UUID: "+stableId);print("Before refill",clinic,"refill");
            clinic.refillBatch(stableId,20,newExpiry,"new note");print("After refill",clinic,"refill");
            Medicine refilled=clinic.medicinePage(0,10,"refill").rows().get(0);
            if(!refilled.getBatchId().equals(stableId)||refilled.getQuantity()!=20||!refilled.getExpirationDate().equals(newExpiry)||!refilled.getBatchDate().equals(LocalDate.now())||!refilled.getRemarks().equals("new note"))throw new AssertionError("refill fields mismatch");
            System.out.println("PASS refill keeps ID and replaces quantity, expiry, batch date, remarks");
            expect(IllegalStateException.class,()->clinic.refillBatch(stableId,1,newExpiry,"still stocked"),"refilling a batch with stock");
            expect(IllegalArgumentException.class,()->clinic.refillBatch("00000000-0000-0000-0000-000000000000",1,newExpiry,"unknown"),"unknown batch UUID");
            seed(database,1,"rollback",oldExpiry,LocalDate.now().minusDays(4),"unchanged");
            database.transaction(c->{try(var s=c.createStatement()){s.execute("ALTER TABLE medicine_batches ADD CONSTRAINT refill_failure CHECK(name <> 'rollback' OR quantity<10)");}return null;});
            Medicine rollback=clinic.medicinePage(0,10,"rollback").rows().get(0);
            expect(StorageException.class,()->clinic.refillBatch(rollback.getBatchId(),10,newExpiry,"must rollback"),"save failure during refill");
            print("After failed save (unchanged)",clinic,"rollback");
            if(clinic.medicinePage(0,10,"rollback").rows().get(0).getQuantity()!=0)throw new AssertionError("failed save changed the row");
        }
        try(Database database=new Database(file)){
            ClinicData clinic=ClinicRepository.create(database);String restored=clinic.medicinePage(0,10,"refill").rows().get(0).getBatchId();
            if(!stableId.equals(restored))throw new AssertionError("batch UUID changed after restart");
            System.out.println("PASS batch UUID remains stable after restart: "+restored);
            database.transaction(c->{try(var s=c.createStatement()){s.execute("DROP TABLE medicine_batch_ids");s.executeUpdate("UPDATE schema_version SET version=4");}return null;});
        }
        String migrated;
        try(Database database=new Database(file)){
            ClinicData clinic=ClinicRepository.create(database);migrated=clinic.medicinePage(0,10,"refill").rows().get(0).getBatchId();
            System.out.println("Old schema without batch identity receives UUID: "+migrated);
        }
        try(Database database=new Database(file)){
            String reopened=ClinicRepository.create(database).medicinePage(0,10,"refill").rows().get(0).getBatchId();
            if(!migrated.equals(reopened))throw new AssertionError("migrated batch UUID was not persisted");
            System.out.println("PASS migrated UUID is saved and stable after another reopen");
        }
    }
}
