package clinic.service;

import clinic.core.ClinicSystem;
import clinic.model.User;
import clinic.model.NurseCampus;
import clinic.model.MedicineProduct;
import java.time.LocalDate;

/** Permission and audit boundary for the ClinicData batch extensions. */
final class SecuredBatchOperations {
    private SecuredBatchOperations() {}
    static void save(ClinicSystem delegate,User user,AuditLog log,String name,int quantity,
            LocalDate expiry,String remarks,boolean restock,boolean merge) {
        String action=action(restock,merge);
        authorize(user,log,action,name);
        log.unitOfWork(()->{
            if(delegate instanceof ClinicData data) {
                data.saveInventoryBatch(new MedicineProduct(name,"",""),quantity,expiry,remarks,
                    restock,merge,user.getUserId(),user.getCampusId());
            } else if(merge) throw new UnsupportedOperationException("Batch merging requires ClinicData.");
            else if(restock) delegate.restockMedicine(name,quantity,expiry);
            else delegate.addMedicine(name,quantity,expiry,remarks);
            record(log,user,action,details(name,quantity,expiry),true);
            return null;
        });
    }
    static void save(ClinicSystem delegate,User user,AuditLog log,MedicineProduct product,int quantity,
            LocalDate expiry,String remarks,boolean restock,boolean merge) {
        String label=product.displayName(),action=action(restock,merge);
        authorize(user,log,action,label);
        log.unitOfWork(()->{
            if(delegate instanceof ClinicData data) {
                data.saveInventoryBatch(product,quantity,expiry,remarks,restock,merge,
                    user.getUserId(),user.getCampusId());
            } else if(merge) throw new UnsupportedOperationException("Batch merging requires ClinicData.");
            else if(restock) delegate.restockMedicine(product,quantity,expiry,remarks);
            else delegate.addMedicine(product,quantity,expiry,remarks);
            record(log,user,action,details(label,quantity,expiry),true);
            return null;
        });
    }
    static void mergeDuplicates(ClinicSystem delegate,User user,AuditLog log,String name,LocalDate expiry) {
        authorize(user,log,"duplicate batches merged",name);
        log.unitOfWork(()->{
            if(!(delegate instanceof ClinicData data))throw new UnsupportedOperationException("Batch merging requires ClinicData.");
            data.mergeDuplicateMedicineBatches(name,expiry);
            record(log,user,"duplicate batches merged","medicine="+name+"; expiry="+expiry+"; duplicate batches consolidated",true);
            return null;
        });
    }
    static void mergeDuplicates(ClinicSystem delegate,User user,AuditLog log,MedicineProduct product,LocalDate expiry) {
        String label=product.displayName();
        authorize(user,log,"duplicate batches merged",label);
        log.unitOfWork(()->{
            if(!(delegate instanceof ClinicData data))throw new UnsupportedOperationException("Batch merging requires ClinicData.");
            data.mergeDuplicateMedicineBatches(product,expiry);
            record(log,user,"duplicate batches merged","medicine="+label+"; expiry="+expiry+"; duplicate batches consolidated",true);
            return null;
        });
    }
    static void refill(ClinicSystem delegate,User user,AuditLog log,String batchId,String medicine,
            int quantity,LocalDate expiry,String remarks) {
        authorize(user,log,"restock reused empty batch",medicine);
        log.unitOfWork(()->{
            if(!(delegate instanceof ClinicData data))throw new UnsupportedOperationException("Batch refill requires ClinicData.");
            data.refillInventoryBatch(batchId,quantity,expiry,remarks,user.getUserId(),user.getCampusId());
            record(log,user,"restock reused empty batch",details(medicine,quantity,expiry),true);
            return null;
        });
    }
    private static String action(boolean restock,boolean merge) {
        if(merge)return restock?"restock merged":"initial stock merged";
        return restock?"restock new batch":"initial stock added";
    }
    private static String details(String medicine,int quantity,LocalDate expiry) {
        return "medicine="+medicine+"; quantity="+quantity+"; expiry="+expiry;
    }
    private static void authorize(User user,AuditLog log,String action,String name) {
        if(!user.canAccess(User.RESTOCK)) {
            record(log,user,action,name,false);
            throw new SecurityException("Access denied.");
        }
        if(!User.ADMIN.equals(user.getRole())&&user.getCampusId()==null) {
            record(log,user,action,"Campus unassigned",false);
            throw new SecurityException("Your account has no assigned campus. Ask an administrator to assign one.");
        }
        if(User.NURSE.equals(user.getRole())&&NurseCampus.forId(user.getCampusId()).isEmpty()) {
            record(log,user,action,"Nurse account requires an approved UIC campus assignment",false);
            throw new SecurityException("Your account needs an approved UIC campus assignment. Ask a Super Admin to update it.");
        }
    }
    private static void record(AuditLog log,User user,String action,String medicine,boolean allowed) {
        log.record(java.time.LocalDateTime.now(),user.getUserId(),user.getName(),user.getRole(),
            action,medicine,allowed,user.getCampusId());
    }
}
