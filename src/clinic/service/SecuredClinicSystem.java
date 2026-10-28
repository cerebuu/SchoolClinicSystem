package clinic.service;

import clinic.core.ClinicSystem;
import clinic.model.*;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import clinic.notify.Channel;

/** Permission and privacy-safe audit boundary around the clinic domain. */
public final class SecuredClinicSystem implements ClinicSystem,ConsultationWorkflow {
    private final ClinicSystem delegate;
    private final User user;
    private final AuditLog log;
    private final java.util.Map<String,Channel> notificationChannels=new java.util.concurrent.ConcurrentHashMap<>();

    public SecuredClinicSystem(ClinicSystem delegate, User user, AuditLog log) {
        this.delegate = java.util.Objects.requireNonNull(delegate);
        this.user = java.util.Objects.requireNonNull(user).immutableCopy();
        this.log = java.util.Objects.requireNonNull(log);
    }

    @Override
    public Optional<Patient> findPatient(String patientId) {
        return call("findPatient", () -> delegate.findPatient(patientId));
    }

    @Override
    public void registerPatient(Patient patient) {
        allergyWrite("registerPatient", patient==null?0:patient.getAllergies().size(), () -> delegate.registerPatient(patient));
    }

    @Override
    public void updatePatient(String patientId, Patient profile) {
        run("updatePatient", () -> delegate.updatePatient(patientId, profile));
    }

    @Override
    public void addAllergy(String patientId, String allergy) {
        allergyWrite("addAllergy", 1, () -> delegate.addAllergy(patientId, allergy));
    }

    @Override
    public MedicalRecord recordVisit(String patientId, List<String> symptoms, String reason, String remarks, String staffId) {
        verifyStaff(staffId);
        return clinicalMutation("recordVisit",()->delegate instanceof ClinicData data
                ?data.recordVisitWithCampus(patientId,symptoms,reason,remarks,staffId,user.getCampusId())
                :delegate.recordVisit(patientId,symptoms,reason,remarks,staffId),
            record->"recordId="+record.getRecordId()+"; symptomCount="+(symptoms==null?0:symptoms.size()));
    }

    @Override
    public MedicalRecord recordVisitAndAdminister(String patientId,List<String> symptoms,String reason,
            String remarks,String staffId,List<DispenseItem> medicines,boolean allergyConfirmed) {
        verifyStaff(staffId);
        return clinicalMutation("recordVisitAndAdminister",()->delegate instanceof ClinicData data
            ?data.recordVisitAndAdministerWithCampus(patientId,symptoms,reason,remarks,staffId,medicines,
                allergyConfirmed,user.getCampusId())
            :delegate.recordVisitAndAdminister(patientId,symptoms,reason,remarks,staffId,medicines,allergyConfirmed),
            record->"symptomCount="+(symptoms==null?0:symptoms.size())
                +"; medicationCount="+(medicines==null?0:medicines.size()),
            ()->{if(allergyConfirmed)auditConfirmationCount(patientId);},
            medicines!=null&&!medicines.isEmpty()?User.DISPENSE:null);
    }

    @Override
    public MedicalRecord recordVisitAndAdministerWithReason(String patientId,List<String> symptoms,String reason,
            String remarks,String staffId,List<DispenseItem> medicines,String overrideReason) {
        verifyStaff(staffId);
        String safeReason=overrideReason==null?"":overrideReason.trim();
        return clinicalMutation("recordVisitAndAdminister",()->delegate instanceof ClinicData data
            ?data.recordVisitAndAdministerWithReason(patientId,symptoms,reason,remarks,staffId,medicines,
                safeReason,user.getCampusId())
            :delegate.recordVisitAndAdminister(patientId,symptoms,reason,remarks,staffId,medicines,!safeReason.isBlank()),
            record->"symptomCount="+(symptoms==null?0:symptoms.size())
                +"; medicationCount="+(medicines==null?0:medicines.size()),
            ()->{if(!safeReason.isBlank())auditConfirmationCount(patientId);},
            medicines!=null&&!medicines.isEmpty()?User.DISPENSE:null);
    }

    @Override
    public List<MedicalRecord> getVisitHistory(String patientId) {
        return call("getVisitHistory", () -> delegate instanceof ClinicData data
            ?data.getVisitHistory(patientId,User.ADMIN.equals(user.getRole())?null:user.getCampusId())
            :delegate.getVisitHistory(patientId));
    }

    @Override
    public List<String> checkSafety(String patientId, String medicineName) {
        return call("checkSafety", () -> delegate.checkSafety(patientId, medicineName));
    }

    @Override
    public MedicineTransaction dispenseMedicine(String patientId, String medicineName, int quantity, String staffId, boolean overrideConfirmed) {
        verifyStaff(staffId);
        return clinicalMutation("dispenseMedicine", () -> delegate instanceof ClinicData data
            ?data.dispenseMedicineWithCampus(patientId,new MedicineProduct(medicineName,"",""),quantity,
                staffId,overrideConfirmed?"Clinical confirmation":"",user.getCampusId())
            :delegate.dispenseMedicine(patientId,medicineName,quantity,staffId,overrideConfirmed),
            transaction->"transactionId="+transaction.getTransactionId()+"; quantity="+transaction.getQuantity(),
            ()->{if(overrideConfirmed)auditConfirmationCount(patientId);});
    }

    @Override
    public void addMedicine(String name, int quantity, LocalDate expiry, String remarks) {
        SecuredBatchOperations.save(delegate,user,log,name,quantity,expiry,remarks,false,false);
    }
    @Override
    public void addMedicine(MedicineProduct medicine,int quantity,LocalDate expiry,String remarks) {
        SecuredBatchOperations.save(delegate,user,log,medicine,quantity,expiry,remarks,false,false);
    }

    @Override
    public MedicineTransaction dispenseMedicine(String patientId, String medicineName, int quantity,
            String staffId, String overrideReason) {
        verifyStaff(staffId);
        return clinicalMutation("dispenseMedicine", () -> {
            if(delegate instanceof ClinicData data) {
                return data.dispenseMedicineWithCampus(patientId,new MedicineProduct(medicineName,"",""),
                    quantity,staffId,overrideReason,user.getCampusId());
            }
            return delegate.dispenseMedicine(patientId,medicineName,quantity,staffId,overrideReason);
        },transaction->"transactionId="+transaction.getTransactionId()+"; quantity="+transaction.getQuantity());
    }

    @Override
    public MedicineTransaction dispenseMedicine(String patientId,MedicineProduct medicine,int quantity,
            String staffId,boolean overrideConfirmed) {
        verifyStaff(staffId);
        return clinicalMutation("dispenseMedicine",()->delegate instanceof ClinicData data
            ?data.dispenseMedicineWithCampus(patientId,medicine,quantity,staffId,
                overrideConfirmed?"Clinical confirmation":"",user.getCampusId())
            :delegate.dispenseMedicine(patientId,medicine,quantity,staffId,overrideConfirmed),
            transaction->"transactionId="+transaction.getTransactionId()+"; quantity="+transaction.getQuantity(),
            ()->{if(overrideConfirmed)auditConfirmationCount(patientId);});
    }

    @Override
    public void restockMedicine(String name, int quantity, LocalDate expiry) {
        SecuredBatchOperations.save(delegate,user,log,name,quantity,expiry,"",true,false);
    }
    @Override
    public void restockMedicine(MedicineProduct medicine,int quantity,LocalDate expiry,String remarks) {
        SecuredBatchOperations.save(delegate,user,log,medicine,quantity,expiry,remarks,true,false);
    }

    public void restockMedicine(String name,int quantity,LocalDate expiry,String remarks) { SecuredBatchOperations.save(delegate,user,log,name,quantity,expiry,remarks,true,false); }

    public void saveMedicineBatch(String name,int quantity,LocalDate expiry,String remarks,boolean restock,boolean merge) {
        SecuredBatchOperations.save(delegate,user,log,name,quantity,expiry,remarks,restock,merge);
    }
    public void saveMedicineBatch(MedicineProduct product,int quantity,LocalDate expiry,String remarks,
            boolean restock,boolean merge) {
        SecuredBatchOperations.save(delegate,user,log,product,quantity,expiry,remarks,restock,merge);
    }

    public void mergeDuplicateMedicineBatches(String name,LocalDate expiry) {
        SecuredBatchOperations.mergeDuplicates(delegate,user,log,name,expiry);
    }
    public void mergeDuplicateMedicineBatches(MedicineProduct product,LocalDate expiry) {
        SecuredBatchOperations.mergeDuplicates(delegate,user,log,product,expiry);
    }
    public void refillBatch(String batchId,String medicine,int quantity,LocalDate expiry,String remarks) {
        SecuredBatchOperations.refill(delegate,user,log,batchId,medicine,quantity,expiry,remarks);
    }

    @Override
    public int getStock(String medicineName) {
        return call("getStock", () -> delegate.getStock(medicineName));
    }
    @Override
    public int getStock(MedicineProduct medicine) {
        return call("getStock",()->delegate.getStock(medicine));
    }

    @Override
    public List<Medicine> getLowStock() {
        return call("getLowStock", () -> delegate.getLowStock());
    }

    @Override
    public List<Medicine> getExpiringWithin(int days) {
        return call("getExpiringWithin", () -> delegate.getExpiringWithin(days));
    }

    @Override
    public double estimateDaysRemaining(String medicineName) {
        return call("estimateDaysRemaining", () -> delegate.estimateDaysRemaining(medicineName));
    }

    @Override
    public List<Alert> detectOutbreaks() {
        return call("detectOutbreaks", () -> delegate instanceof ClinicData data
            ?data.detectOutbreaks(campusScope()):delegate.detectOutbreaks());
    }

    @Override
    public Optional<Alert> detectRecurringVisit(String patientId) {
        return call("detectRecurringVisit", () -> delegate instanceof ClinicData data
            ?data.detectRecurringVisit(patientId,campusScope()):delegate.detectRecurringVisit(patientId));
    }

    @Override
    public List<Alert> getAllAlerts() {
        return call("getAllAlerts", () -> delegate instanceof ClinicData data
            ?data.getAllAlerts(campusScope()):delegate.getAllAlerts());
    }

    @Override
    public String generateReport(Formattable item) {
        return call("generateReport", () -> delegate.generateReport(item));
    }

    @Override
    public List<MedicalRecord> searchRecords(String patientId, String symptom, String medicineName, LocalDate date, String section, String department) {
        return call("searchRecords", () -> delegate instanceof ClinicData data
            ?data.searchRecords(patientId,symptom,medicineName,date,section,department,campusScope())
            :delegate.searchRecords(patientId,symptom,medicineName,date,section,department));
    }

    @Override
    public NotificationLog createGuardianNotification(String patientId, String transactionId, String reason) {
        return clinicalMutation("createGuardianNotification", () -> delegate instanceof ClinicData data
            ?data.createGuardianNotificationWithCampus(patientId,transactionId,reason,user.getCampusId())
            :delegate.createGuardianNotification(patientId,transactionId,reason),
            notification->"notificationId="+notification.getNotificationId());
    }

    @Override
    public void updateNotificationStatus(String notificationId, NotificationStatus status) {
        requireCampus();
        run("updateNotificationStatus", () -> {
            if(delegate instanceof ClinicData data)
                data.updateNotificationStatusForCampus(notificationId,status,user.getCampusId());
            else delegate.updateNotificationStatus(notificationId,status);
        });
    }

    @Override
    public List<NotificationLog> getNotifications(String patientId) {
        return call("getNotifications", () -> delegate instanceof ClinicData data
            ?data.getNotificationsForCampus(patientId,user.getCampusId())
            :delegate.getNotifications(patientId));
    }

    @Override
    public NotificationLog createEmergencyNotification(String patientId) {
        return clinicalMutation("createEmergencyNotification",()->delegate instanceof ClinicData data
            ?data.createEmergencyNotificationWithCampus(patientId,user.getCampusId())
            :delegate.createEmergencyNotification(patientId),
            notification->"notificationId="+notification.getNotificationId());
    }

    @Override
    public NotificationLog beginNotificationAttempt(String id,Channel channel,LocalDateTime attemptedAt) {
        requireCampus();
        NotificationLog started=call("beginNotificationAttempt",
            ()->delegate instanceof ClinicData data
                ?data.beginNotificationAttemptForCampus(id,channel,attemptedAt,user.getCampusId())
                :delegate.beginNotificationAttempt(id,channel,attemptedAt));
        notificationChannels.put(id,channel);
        return started;
    }

    @Override
    public void completeNotificationAttempt(String id,NotificationStatus status) {
        requireCampus();
        if(!user.canAccess(User.SEND_NOTIFICATIONS)) {
            audit("notification send denied","notificationId="+id,false);
            throw new SecurityException("Only nurses and nurse assistants can send notifications.");
        }
        Channel channel=notificationChannels.get(id);
        if(channel==null)throw new IllegalStateException("No active notification attempt.");
        log.unitOfWork(()->{
            if(delegate instanceof ClinicData data)
                data.completeNotificationAttemptForCampus(id,status,user.getCampusId());
            else delegate.completeNotificationAttempt(id,status);
            String action=status==NotificationStatus.SENT?"notification sent":"notification failed";
            audit(action,"channel="+channel+"; notificationId="+id,true);
            return null;
        });
        notificationChannels.remove(id);
    }

    private void verifyStaff(String staffId) {
        requireCampus();
        if (!user.getUserId().equals(staffId)) {
            audit("staffIdentity", "Identity mismatch", false);
            throw new SecurityException("Staff identity must match the signed-in user.");
        }
    }
    private void require(String permission,String action) {
        if(!user.canAccess(permission)) {
            audit(action,"Permission denied",false);
            throw new SecurityException("Access denied.");
        }
    }
    private void requireCampus() {
        if(!User.ADMIN.equals(user.getRole())&&user.getCampusId()==null) {
            audit("campus context denied","Clinical staff account has no assigned campus",false);
            throw new SecurityException("Your account has no assigned campus. Ask an administrator to assign one.");
        }
        if(User.NURSE.equals(user.getRole())&&NurseCampus.forId(user.getCampusId()).isEmpty()) {
            audit("campus context denied","Nurse account requires an approved UIC campus assignment",false);
            throw new SecurityException("Your account needs an approved UIC campus assignment. Ask a Super Admin to update it.");
        }
    }
    private void auditConfirmationCount(String patientId) {
        int count=delegate.findPatient(patientId).orElseThrow().getAllergies().size();
        if(count>0)audit("dispense confirmation", "count=1", true);
    }
    private String campusScope() {
        return User.ADMIN.equals(user.getRole())?null:user.getCampusId();
    }
    private <T> T call(String action, Supplier<T> work) {
        boolean allowed = allowed(action);
        if (!allowed) {
            audit(action,"Clinic operation",false);
            throw new SecurityException("Access denied.");
        }
        requireCampus();
        try {
            return log.unitOfWork(()->{
                T result=work.get();
                audit(action,"Clinic operation",true);
                return result;
            });
        } catch(SecurityException denied) {
            audit(action,"Access denied",false);
            throw denied;
        }
    }

    /** Clinical mutation events are written only after the domain operation succeeds. */
    private <T> T clinicalMutation(String action,Supplier<T> work,
            java.util.function.Function<T,String> details,String... additionalPermissions) {
        return clinicalMutation(action,work,details,()->{},additionalPermissions);
    }
    private <T> T clinicalMutation(String action,Supplier<T> work,
            java.util.function.Function<T,String> details,Runnable successAudit,String... additionalPermissions) {
        boolean permitted=allowed(action);
        if(permitted&&additionalPermissions!=null) {
            for(String permission:additionalPermissions)
                if(permission!=null&&!user.canAccess(permission)) { permitted=false;break; }
        }
        if(!permitted) {
            audit(action,"Permission denied",false);
            throw new SecurityException("Access denied.");
        }
        requireCampus();
        return log.unitOfWork(()->{
            T result=work.get();
            audit(action,details.apply(result),true);
            successAudit.run();
            return result;
        });
    }

    private void run(String action, Runnable work) {
        call(action, () -> { work.run(); return null; });
    }
    private void allergyWrite(String action, int count, Runnable work) {
        boolean permitted = allowed(action);
        if (!permitted) {
            audit(action,"count="+count,false);
            throw new SecurityException("Access denied.");
        }
        requireCampus();
        log.unitOfWork(()->{
            work.run();
            audit(action,"count="+count,true);
            return null;
        });
    }

    private void audit(String action, String details, boolean allowed) {
        log.record(LocalDateTime.now(), user.getUserId(), user.getName(),
            user.getRole(), action, details, allowed,user.getCampusId());
    }

    private boolean allowed(String action) {
        if (action.equals("generateReport")) {
            return user.canAccess(User.REPORTS) || user.canAccess(User.VIEW_REPORTS);
        }
        String feature = switch (action) {
            case "findPatient", "registerPatient", "updatePatient", "addAllergy", "recordVisit",
                "recordVisitAndAdminister",
                "createGuardianNotification", "updateNotificationStatus", "getNotifications" -> User.RECORD_TRANSACTION;
            case "createEmergencyNotification" -> User.RECORD_TRANSACTION;
            case "beginNotificationAttempt", "completeNotificationAttempt" -> User.SEND_NOTIFICATIONS;
            case "getVisitHistory", "searchRecords", "detectOutbreaks",
                "detectRecurringVisit", "getAllAlerts" -> User.RECORDS;
            case "checkSafety", "dispenseMedicine" -> User.DISPENSE;
            case "addMedicine", "restockMedicine" -> User.RESTOCK;
            case "getStock", "getLowStock", "getExpiringWithin", "estimateDaysRemaining" -> User.VIEW_STOCK;
            default -> throw new IllegalArgumentException("Unknown clinic action.");
        };
        return user.canAccess(feature);
    }
}
