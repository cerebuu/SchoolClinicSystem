import clinic.model.MedicineProduct;
import clinic.model.SchoolNurse;
import clinic.service.AuditEntry;
import clinic.service.AuditLog;
import clinic.service.InMemoryClinicSystem;
import clinic.service.SecuredClinicSystem;
import java.time.LocalDate;
import java.util.List;

/** Checks audit labels for distinct inventory stock actions. */
public final class InventoryAuditChecks {
    public static void main(String[] args) {
        LocalDate expiry=LocalDate.now().plusDays(60);
        MedicineProduct product=new MedicineProduct("Paracetamol","N02BE01","");
        InMemoryClinicSystem data=new InMemoryClinicSystem();
        data.addMedicine(product,5,expiry,"batch one");
        data.addMedicine(product,3,expiry,"batch two");
        SchoolNurse nurse=new SchoolNurse("N1","Nurse");
        nurse.setCampusId("uic-bajada");
        AuditLog audit=new AuditLog();
        SecuredClinicSystem clinic=new SecuredClinicSystem(data,nurse,audit);

        clinic.saveMedicineBatch(product,2,expiry,"initial addition",false,true);
        clinic.saveMedicineBatch(product,4,expiry,"new stock",true,true);
        clinic.mergeDuplicateMedicineBatches(product,expiry);

        List<AuditEntry> events=audit.getAll();
        check(events.size()==3,"all three stock operations are audited");
        check(event(events,"initial stock merged").getDetails().contains("quantity=2")
                &&"uic-bajada".equals(event(events,"initial stock merged").getCampusId()),
            "adding initial stock to an existing batch keeps its action and campus");
        check(event(events,"restock merged").getDetails().contains("quantity=4"),
            "restocking an existing batch has a distinct action");
        check(event(events,"duplicate batches merged").getDetails().contains("duplicate batches consolidated"),
            "consolidating duplicate batches is labeled separately");
        System.out.println("Inventory audit checks passed: 4");
    }

    private static AuditEntry event(List<AuditEntry> events,String action) {
        return events.stream().filter(entry->entry.getAction().equals(action)).findFirst()
            .orElseThrow(()->new AssertionError("Missing audit action: "+action));
    }

    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
}
