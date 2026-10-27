package clinic.service;

import clinic.core.ClinicSystem;
import clinic.model.*;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import clinic.notify.Channel;

/** Permission and privacy-safe audit boundary around the clinic domain. */
public final class SecuredClinicSystem implements ClinicSystem {
    private final ClinicSystem delegate;
    private final User user;
    private final AuditLog log;
    private final java.util.Map<String,Channel> notificationChannels=new java.util.concurrent.ConcurrentHashMap<>();

    public SecuredClinicSystem(ClinicSystem delegate, User user, AuditLog log) {
        this.delegate = java.util.Objects.requireNonNull(delegate);
        this.user = java.util.Objects.requireNonNull(user);
        this.log = java.util.Objects.requireNonNull(log);
    }

    @Override
    public Optional<Patient> findPatient(String patientId) {
        return call("findPatient", () -> delegate.findPatient(patientId));
    }

    @Override
    public void registerPatient(Patient patient) {
        allergyWrite("registerPatient", patient.getAllergies().size(), () -> delegate.registerPatient(patient));
    }

    @Override
    public void addAllergy(String patientId, String allergy) {
        allergyWrite("addAllergy", 1, () -> delegate.addAllergy(patientId, allergy));
    }

    @Override
    public MedicalRecord recordVisit(String patientId, List<String> symptoms, String reason, String remarks, String staffId) {
        verifyStaff(staffId);
        return call("recordVisit", () -> delegate instanceof ClinicData data
            ?data.recordVisitWithCampus(patientId,symptoms,reason,remarks,staffId,user.getCampusId())
            :delegate.recordVisit(patientId,symptoms,reason,remarks,staffId));
    }

    @Override
    public MedicalRecord recordVisitAndAdminister(String patientId,List<String> symptoms,String reason,
            String remarks,String staffId,List<DispenseItem> medicines,boolean allergyConfirmed) {
        verifyStaff(staffId);
        require(User.RECORD_TRANSACTION,"recordVisitAndAdminister");
        if(medicines!=null&&!medicines.isEmpty())require(User.DISPENSE,"recordVisitAndAdminister");
        MedicalRecord visit=call("recordVisitAndAdminister",()->delegate instanceof ClinicData data
            ?data.recordVisitAndAdministerWithCampus(patientId,symptoms,reason,remarks,staffId,medicines,
                allergyConfirmed,user.getCampusId())
            :delegate.recordVisitAndAdminister(patientId,symptoms,reason,remarks,staffId,medicines,allergyConfirmed));
        if(allergyConfirmed)auditAllergyConfirmation(patientId);
        return visit;
    }

    @Override
    public List<MedicalRecord> getVisitHistory(String patientId) {
        return call("getVisitHistory", () -> delegate.getVisitHistory(patientId));
    }

    @Override
    public List<String> checkSafety(String patientId, String medicineName) {
        return call("checkSafety", () -> delegate.checkSafety(patientId, medicineName));
    }

    @Override
    public MedicineTransaction dispenseMedicine(String patientId, String medicineName, int quantity, String staffId, boolean overrideConfirmed) {
        verifyStaff(staffId);
        require(User.DISPENSE,"dispenseMedicine");
        MedicineTransaction result = call("dispenseMedicine", () -> delegate instanceof ClinicData data
            ?data.dispenseMedicineWithCampus(patientId,new MedicineProduct(medicineName,"",""),quantity,
                staffId,overrideConfirmed?"Allergies reviewed by staff":"",user.getCampusId())
            :delegate.dispenseMedicine(patientId,medicineName,quantity,staffId,overrideConfirmed));
        if (overrideConfirmed) auditAllergyConfirmation(patientId);
        return result;
    }

    @Override
    public void addMedicine(String name, int quantity, LocalDate expiry, String remarks) {
        run("addMedicine", () -> delegate.addMedicine(name, quantity, expiry, remarks));
    }
    @Override
    public void addMedicine(MedicineProduct medicine,int quantity,LocalDate expiry,String remarks) {
        run("addMedicine",()->delegate.addMedicine(medicine,quantity,expiry,remarks));
    }

    @Override
    public MedicineTransaction dispenseMedicine(String patientId, String medicineName, int quantity,
            String staffId, String overrideReason) {
        verifyStaff(staffId);
        require(User.DISPENSE,"dispenseMedicine");
        MedicineTransaction result = call("dispenseMedicine", () -> {
            if(delegate instanceof ClinicData data) {
                return data.dispenseMedicineWithCampus(patientId,new MedicineProduct(medicineName,"",""),
                    quantity,staffId,overrideReason,user.getCampusId());
            }
            return delegate.dispenseMedicine(patientId,medicineName,quantity,staffId,overrideReason);
        });
        if (overrideReason != null && !overrideReason.isBlank()) auditAllergyConfirmation(patientId);
        return result;
    }

    @Override
    public MedicineTransaction dispenseMedicine(String patientId,MedicineProduct medicine,int quantity,
            String staffId,boolean overrideConfirmed) {
        verifyStaff(staffId);
        require(User.DISPENSE,"dispenseMedicine");
        MedicineTransaction result=call("dispenseMedicine",()->delegate instanceof ClinicData data
            ?data.dispenseMedicineWithCampus(patientId,medicine,quantity,staffId,
                overrideConfirmed?"Allergies reviewed by staff":"",user.getCampusId())
            :delegate.dispenseMedicine(patientId,medicine,quantity,staffId,overrideConfirmed));
        if(overrideConfirmed)auditAllergyConfirmation(patientId);
        return result;
    }

    @Override
    public void restockMedicine(String name, int quantity, LocalDate expiry) {
        run("restockMedicine", () -> delegate.restockMedicine(name, quantity, expiry));
    }
    @Override
    public void restockMedicine(MedicineProduct medicine,int quantity,LocalDate expiry,String remarks) {
        run("restockMedicine",()->delegate.restockMedicine(medicine,quantity,expiry,remarks));
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
        return call("detectOutbreaks", () -> delegate.detectOutbreaks());
    }

    @Override
    public Optional<Alert> detectRecurringVisit(String patientId) {
        return call("detectRecurringVisit", () -> delegate.detectRecurringVisit(patientId));
    }

    @Override
    public List<Alert> getAllAlerts() {
        return call("getAllAlerts", () -> delegate.getAllAlerts());
    }

    @Override
    public String generateReport(Formattable item) {
        return call("generateReport", () -> delegate.generateReport(item));
    }

    @Override
    public List<MedicalRecord> searchRecords(String patientId, String symptom, String medicineName, LocalDate date, String section, String department) {
        return call("searchRecords", () -> delegate.searchRecords(patientId, symptom, medicineName, date, section, department));
    }

    @Override
    public NotificationLog createGuardianNotification(String patientId, String transactionId, String reason) {
        return call("createGuardianNotification", () -> delegate.createGuardianNotification(patientId, transactionId, reason));
    }

    @Override
    public void updateNotificationStatus(String notificationId, NotificationStatus status) {
        requireCampus();
        run("updateNotificationStatus", () -> delegate.updateNotificationStatus(notificationId, status));
    }

    @Override
    public List<NotificationLog> getNotifications(String patientId) {
        return call("getNotifications", () -> delegate.getNotifications(patientId));
    }

    @Override
    public NotificationLog createEmergencyNotification(String patientId) {
        return call("createEmergencyNotification",()->delegate.createEmergencyNotification(patientId));
    }

    @Override
    public NotificationLog beginNotificationAttempt(String id,Channel channel,LocalDateTime attemptedAt) {
        requireCampus();
        NotificationLog started=call("beginNotificationAttempt",
            ()->delegate.beginNotificationAttempt(id,channel,attemptedAt));
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
        delegate.completeNotificationAttempt(id,status);
        String action=status==NotificationStatus.SENT?"notification sent":"notification failed";
        audit(action,"channel="+channel+"; notificationId="+id,true);
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
    }
    private void auditAllergyConfirmation(String patientId) {
        int count=delegate.findPatient(patientId).orElseThrow().getAllergies().size();
        if(count>0)audit("dispense with allergy confirmation", "Allergy count: "+count, true);
    }

    private <T> T call(String action, Supplier<T> work) {
        boolean allowed = allowed(action);
        if (!allowed) {
            audit(action,"Clinic operation",false);
            throw new SecurityException("Access denied.");
        }
        requireCampus();
        audit(action,"Clinic operation",true);
        return work.get();
    }

    private void run(String action, Runnable work) {
        call(action, () -> { work.run(); return null; });
    }
    private void allergyWrite(String action, int count, Runnable work) {
        boolean permitted = allowed(action);
        if (!permitted) {
            audit(action,"Allergy count: "+count,false);
            throw new SecurityException("Access denied.");
        }
        requireCampus();
        audit(action,"Allergy count: "+count,true);
        work.run();
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
            case "findPatient", "registerPatient", "addAllergy", "recordVisit",
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
