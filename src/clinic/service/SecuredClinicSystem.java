package clinic.service;

import clinic.core.ClinicSystem;
import clinic.model.*;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Permission and privacy-safe audit boundary around the clinic domain. */
public final class SecuredClinicSystem implements ClinicSystem {
    private final ClinicSystem delegate;
    private final User user;
    private final AuditLog log;

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
        run("registerPatient", () -> delegate.registerPatient(patient));
    }

    @Override
    public void addAllergy(String patientId, String allergy) {
        run("addAllergy", () -> delegate.addAllergy(patientId, allergy));
    }

    @Override
    public MedicalRecord recordVisit(String patientId, List<String> symptoms, String reason, String remarks, String staffId) {
        verifyStaff(staffId);
        return call("recordVisit", () -> delegate.recordVisit(patientId, symptoms, reason, remarks, staffId));
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
        MedicineTransaction result = call("dispenseMedicine", () -> delegate.dispenseMedicine(patientId, medicineName, quantity, staffId, overrideConfirmed));
        if (overrideConfirmed) audit("safetyOverride", "Confirmed by signed-in staff", true);
        return result;
    }

    @Override
    public void addMedicine(String name, int quantity, LocalDate expiry, String remarks) {
        run("addMedicine", () -> delegate.addMedicine(name, quantity, expiry, remarks));
    }

    @Override
    public void restockMedicine(String name, int quantity, LocalDate expiry) {
        run("restockMedicine", () -> delegate.restockMedicine(name, quantity, expiry));
    }

    @Override
    public int getStock(String medicineName) {
        return call("getStock", () -> delegate.getStock(medicineName));
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
        run("updateNotificationStatus", () -> delegate.updateNotificationStatus(notificationId, status));
    }

    @Override
    public List<NotificationLog> getNotifications(String patientId) {
        return call("getNotifications", () -> delegate.getNotifications(patientId));
    }

    private void verifyStaff(String staffId) {
        if (!user.getUserId().equals(staffId)) {
            audit("staffIdentity", "Identity mismatch", false);
            throw new SecurityException("Staff identity must match the signed-in user.");
        }
    }

    private <T> T call(String action, Supplier<T> work) {
        boolean allowed = allowed(action);
        audit(action, "Clinic operation", allowed);
        if (!allowed) throw new SecurityException("Access denied.");
        return work.get();
    }

    private void run(String action, Runnable work) {
        call(action, () -> { work.run(); return null; });
    }

    private void audit(String action, String details, boolean allowed) {
        log.record(LocalDateTime.now(), user.getUserId(), user.getName(),
            user.getRole(), action, details, allowed);
    }

    private boolean allowed(String action) {
        if (action.equals("generateReport")) {
            return user.canAccess(User.REPORTS) || user.canAccess(User.VIEW_REPORTS);
        }
        String feature = switch (action) {
            case "findPatient", "registerPatient", "addAllergy", "recordVisit",
                "createGuardianNotification", "updateNotificationStatus", "getNotifications" -> User.RECORD_TRANSACTION;
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
