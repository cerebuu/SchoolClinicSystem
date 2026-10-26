package clinic.service;

import clinic.core.ClinicSystem;
import clinic.model.User;
import java.time.LocalDate;

/** Permission and audit boundary for the ClinicData batch extensions. */
final class SecuredBatchOperations {
    private SecuredBatchOperations() {}
    static void save(ClinicSystem delegate,User user,AuditLog log,String name,int quantity,
            LocalDate expiry,String remarks,boolean restock,boolean merge) {
        String action=merge?"restock merged":"restock new batch";
        authorize(user,log,action,name);
        if(merge) {
            if(!(delegate instanceof ClinicData data))throw new UnsupportedOperationException("Batch merging requires ClinicData.");
            data.mergeMedicineBatch(name,quantity,expiry,remarks);
        } else if(restock) {
            if(!(delegate instanceof ClinicData data))throw new UnsupportedOperationException("Batch remarks require ClinicData.");
            data.restockMedicine(name,quantity,expiry,remarks);
        } else delegate.addMedicine(name,quantity,expiry,remarks);
        record(log,user,action,name,true);
    }
    static void mergeDuplicates(ClinicSystem delegate,User user,AuditLog log,String name,LocalDate expiry) {
        authorize(user,log,"restock merged",name);
        if(!(delegate instanceof ClinicData data))throw new UnsupportedOperationException("Batch merging requires ClinicData.");
        data.mergeDuplicateMedicineBatches(name,expiry);
        record(log,user,"restock merged",name,true);
    }
    static void refill(ClinicSystem delegate,User user,AuditLog log,String batchId,String medicine,
            int quantity,LocalDate expiry,String remarks) {
        authorize(user,log,"restock reused empty batch",medicine);
        if(!(delegate instanceof ClinicData data))throw new UnsupportedOperationException("Batch refill requires ClinicData.");
        data.refillBatch(batchId,quantity,expiry,remarks);
        record(log,user,"restock reused empty batch",medicine,true);
    }
    private static void authorize(User user,AuditLog log,String action,String name) {
        if(!user.canAccess(User.RESTOCK)) {
            record(log,user,action,name,false);
            throw new SecurityException("Access denied.");
        }
    }
    private static void record(AuditLog log,User user,String action,String medicine,boolean allowed) {
        log.record(java.time.LocalDateTime.now(),user.getUserId(),user.getName(),user.getRole(),action,medicine,allowed);
    }
}
