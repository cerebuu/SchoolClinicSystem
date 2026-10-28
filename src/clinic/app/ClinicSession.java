package clinic.app;
import clinic.core.*;
import clinic.model.User;
import clinic.service.*;
import java.lang.reflect.*;
import java.util.Map;
import java.util.function.Consumer;

/** Session validation and success-only publication around the secured service. */
final class ClinicSession {
    private ClinicSession() {}
    static ClinicSystem open(Login login,ClinicData store,AuditLog audit,DataChangeBus bus) {
        synchronized(login) {
            var owner=login.getCurrentUser().orElseThrow(()->new SecurityException("Sign in required."));
            if(!User.ADMIN.equals(owner.getRole())&&owner.getCampusId()==null)
                throw new SecurityException("Your account has no assigned campus. Ask an administrator to assign one.");
            if(User.NURSE.equals(owner.getRole())&&clinic.model.NurseCampus.forId(owner.getCampusId()).isEmpty())
                throw new SecurityException("Your account needs an approved UIC campus assignment. Ask a Super Admin to update it.");
            long version=login.getSessionVersion();
            var secured=new SecuredClinicSystem(store,owner,audit);
            return (ClinicSystem)Proxy.newProxyInstance(ClinicSystem.class.getClassLoader(),
                new Class<?>[]{ClinicSystem.class,ConsultationWorkflow.class},(p,m,a)->{
                synchronized(login) {
                    if(login.getCurrentUser().orElse(null)!=owner||login.getSessionVersion()!=version)throw new SecurityException("Session expired.");
                    try {
                        Object result=m.invoke(secured,a);ClinicChangePublisher.changed(bus,m.getName());return result;
                    }catch(InvocationTargetException ex){throw ex.getCause();}
                }
            });
        }
    }
    static Consumer<Map<String,String>> restock(Login login,ClinicData store,AuditLog audit,DataChangeBus bus,String medicine) {
        synchronized(login) {
            var owner=login.getCurrentUser().orElseThrow(()->new SecurityException("Sign in required."));
            long version=login.getSessionVersion();
            var secured=new SecuredClinicSystem(store,owner,audit);
            return values->{synchronized(login){
                if(login.getCurrentUser().orElse(null)!=owner||login.getSessionVersion()!=version)throw new SecurityException("Session expired.");
                secured.saveMedicineBatch(medicine,Integer.parseInt(values.get("Quantity")),
                    java.time.LocalDate.parse(values.get("Expiry (YYYY-MM-DD)")),values.get("Remarks"),true,false);
                ClinicChangePublisher.changed(bus,"restockMedicine");
            }};
        }
    }
    static Consumer<Map<String,String>> inventoryBatch(Login login,ClinicData store,AuditLog audit,DataChangeBus bus,
            String medicine,boolean restock,boolean merge) {
        synchronized(login) {
            var owner=login.getCurrentUser().orElseThrow(()->new SecurityException("Sign in required."));
            long version=login.getSessionVersion();
            var secured=new SecuredClinicSystem(store,owner,audit);
            return values->{synchronized(login){
                if(login.getCurrentUser().orElse(null)!=owner||login.getSessionVersion()!=version)throw new SecurityException("Session expired.");
                secured.saveMedicineBatch(medicine,Integer.parseInt(values.get("Quantity")),
                    java.time.LocalDate.parse(values.get("Expiry (YYYY-MM-DD)")),values.get("Remarks"),restock,merge);
                ClinicChangePublisher.changed(bus,"restockMedicine");
            }};
        }
    }
    static Consumer<Map<String,String>> inventoryBatch(Login login,ClinicData store,AuditLog audit,DataChangeBus bus,
            clinic.model.MedicineProduct medicine,boolean restock,boolean merge) {
        synchronized(login) {
            var owner=login.getCurrentUser().orElseThrow(()->new SecurityException("Sign in required."));
            long version=login.getSessionVersion();
            var secured=new SecuredClinicSystem(store,owner,audit);
            return values->{synchronized(login){
                if(login.getCurrentUser().orElse(null)!=owner||login.getSessionVersion()!=version)throw new SecurityException("Session expired.");
                secured.saveMedicineBatch(medicine,Integer.parseInt(values.get("Quantity")),
                    java.time.LocalDate.parse(values.get("Expiry (YYYY-MM-DD)")),values.get("Remarks"),restock,merge);
                ClinicChangePublisher.changed(bus,"restockMedicine");
            }};
        }
    }
    static Consumer<Map<String,String>> refillBatch(Login login,ClinicData store,AuditLog audit,DataChangeBus bus,
            String batchId,String medicine) {
        synchronized(login) {
            var owner=login.getCurrentUser().orElseThrow(()->new SecurityException("Sign in required."));
            long version=login.getSessionVersion();
            var secured=new SecuredClinicSystem(store,owner,audit);
            return values->{synchronized(login){
                if(login.getCurrentUser().orElse(null)!=owner||login.getSessionVersion()!=version)throw new SecurityException("Session expired.");
                secured.refillBatch(batchId,medicine,Integer.parseInt(values.get("Quantity")),
                    java.time.LocalDate.parse(values.get("Expiry (YYYY-MM-DD)")),values.get("Remarks"));
                ClinicChangePublisher.changed(bus,"restockMedicine");
            }};
        }
    }
    static Consumer<clinic.service.BatchMatcher.Group> mergeDuplicates(Login login,ClinicData store,AuditLog audit,DataChangeBus bus) {
        synchronized(login) {
            var owner=login.getCurrentUser().orElseThrow(()->new SecurityException("Sign in required."));
            long version=login.getSessionVersion();
            var secured=new SecuredClinicSystem(store,owner,audit);
            return group->{synchronized(login){
                if(login.getCurrentUser().orElse(null)!=owner||login.getSessionVersion()!=version)throw new SecurityException("Session expired.");
                secured.mergeDuplicateMedicineBatches(group.product(),group.expiry());
                ClinicChangePublisher.changed(bus,"restockMedicine");
            }};
        }
    }
}
