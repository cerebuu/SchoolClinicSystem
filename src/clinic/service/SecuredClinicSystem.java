package clinic.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import clinic.core.ClinicSystem;
import clinic.model.Alert;
import clinic.model.Formattable;
import clinic.model.MedicalRecord;
import clinic.model.Medicine;
import clinic.model.MedicineTransaction;
import clinic.model.NotificationLog;
import clinic.model.NotificationStatus;
import clinic.model.Patient;
import clinic.model.User;

// This class is responsible for enforcing user permissions around a clinic system.
public class SecuredClinicSystem implements ClinicSystem {

    private final ClinicSystem delegate;
    private final User user;
    private final AuditLog log;

    public SecuredClinicSystem(ClinicSystem delegate, User user, AuditLog log) {
        if (delegate == null || user == null || log == null) {
            throw new IllegalArgumentException("Delegate, user, and audit log are required.");
        }
        this.delegate = delegate;
        this.user = user;
        this.log = log;
    }

    @Override
    public Optional<Patient> findPatient(String patientId) {
        return call("findPatient",
                "patientId=" + patientId, new Call<Optional<Patient>>() {
                    public Optional<Patient> run() {
                        return delegate.findPatient(patientId);
                    }
                });
    }

    @Override
    public void registerPatient(Patient patient) {
        check("registerPatient", "patient registration");
        delegate.registerPatient(patient);
    }

    @Override
    public void addAllergy(String patientId, String allergy) {
        check("addAllergy", "patientId=" + patientId);
        delegate.addAllergy(patientId, allergy);
    }

    @Override
    public MedicalRecord recordVisit(String patientId, List<String> symptoms,
                                     String reason, String remarks, String staffId) {
        return call("recordVisit",
                "patientId=" + patientId + ", staffId=" + staffId,
                new Call<MedicalRecord>() {
                    public MedicalRecord run() {
                        return delegate.recordVisit(patientId, symptoms, reason, remarks, staffId);
                    }
                });
    }

    @Override
    public List<MedicalRecord> getVisitHistory(String patientId) {
        return call("getVisitHistory", "patientId=" + patientId,
                new Call<List<MedicalRecord>>() {
                    public List<MedicalRecord> run() {
                        return delegate.getVisitHistory(patientId);
                    }
                });
    }

    @Override
    public List<String> checkSafety(String patientId, String medicineName) {
        return call("checkSafety",
                "patientId=" + patientId + ", medicine=" + medicineName,
                new Call<List<String>>() {
                    public List<String> run() {
                        return delegate.checkSafety(patientId, medicineName);
                    }
                });
    }

    @Override
    public MedicineTransaction dispenseMedicine(String patientId, String medicineName,
                                                int quantity, String staffId,
                                                boolean overrideConfirmed) {
        check("dispenseMedicine",
                "patientId=" + patientId + ", medicine=" + medicineName
                        + ", quantity=" + quantity + ", staffId=" + staffId);
        if (overrideConfirmed) {
            log.record(LocalDateTime.now(), user.getUserId(), user.getName(),
                    user.getRole(), "dispenseMedicine",
                    "SAFETY OVERRIDE staffId=" + staffId, true);
        }
        return delegate.dispenseMedicine(patientId, medicineName, quantity, staffId,
                overrideConfirmed);
    }

    @Override
    public void addMedicine(String name, int quantity, LocalDate expiry, String remarks) {
        check("addMedicine",
                "medicine=" + name + ", quantity=" + quantity);
        delegate.addMedicine(name, quantity, expiry, remarks);
    }

    @Override
    public void restockMedicine(String name, int quantity, LocalDate expiry) {
        check("restockMedicine",
                "medicine=" + name + ", quantity=" + quantity);
        delegate.restockMedicine(name, quantity, expiry);
    }

    @Override
    public int getStock(String medicineName) {
        return call("getStock", "medicine=" + medicineName,
                new Call<Integer>() {
                    public Integer run() {
                        return delegate.getStock(medicineName);
                    }
                });
    }

    @Override
    public List<Medicine> getLowStock() {
        return call("getLowStock", "stock lookup",
                new Call<List<Medicine>>() {
                    public List<Medicine> run() {
                        return delegate.getLowStock();
                    }
                });
    }

    @Override
    public List<Medicine> getExpiringWithin(int days) {
        return call("getExpiringWithin", "days=" + days,
                new Call<List<Medicine>>() {
                    public List<Medicine> run() {
                        return delegate.getExpiringWithin(days);
                    }
                });
    }

    @Override
    public double estimateDaysRemaining(String medicineName) {
        return call("estimateDaysRemaining", "medicine=" + medicineName,
                new Call<Double>() {
                    public Double run() {
                        return delegate.estimateDaysRemaining(medicineName);
                    }
                });
    }

    @Override
    public List<Alert> detectOutbreaks() {
        return call("detectOutbreaks", "alert lookup",
                new Call<List<Alert>>() {
                    public List<Alert> run() {
                        return delegate.detectOutbreaks();
                    }
                });
    }

    @Override
    public Optional<Alert> detectRecurringVisit(String patientId) {
        return call("detectRecurringVisit", "patientId=" + patientId,
                new Call<Optional<Alert>>() {
                    public Optional<Alert> run() {
                        return delegate.detectRecurringVisit(patientId);
                    }
                });
    }

    @Override
    public List<Alert> getAllAlerts() {
        return call("getAllAlerts", "alert lookup",
                new Call<List<Alert>>() {
                    public List<Alert> run() {
                        return delegate.getAllAlerts();
                    }
                });
    }

    @Override
    public String generateReport(Formattable item) {
        return call("generateReport", "report request",
                new Call<String>() {
                    public String run() {
                        return delegate.generateReport(item);
                    }
                });
    }

    @Override
    public List<MedicalRecord> searchRecords(String patientId, String symptom,
                                             String medicineName, LocalDate date,
                                             String section, String department) {
        return call("searchRecords", "patientId=" + patientId,
                new Call<List<MedicalRecord>>() {
                    public List<MedicalRecord> run() {
                        return delegate.searchRecords(patientId, symptom, medicineName,
                                date, section, department);
                    }
                });
    }

    @Override
    public NotificationLog createGuardianNotification(String patientId,
                                                       String transactionId,
                                                       String reason) {
        return call("createGuardianNotification",
                "patientId=" + patientId + ", transactionId=" + transactionId,
                new Call<NotificationLog>() {
                    public NotificationLog run() {
                        return delegate.createGuardianNotification(patientId, transactionId, reason);
                    }
                });
    }

    @Override
    public void updateNotificationStatus(String notificationId, NotificationStatus status) {
        check("updateNotificationStatus",
                "notificationId=" + notificationId);
        delegate.updateNotificationStatus(notificationId, status);
    }

    @Override
    public List<NotificationLog> getNotifications(String patientId) {
        return call("getNotifications",
                "patientId=" + patientId,
                new Call<List<NotificationLog>>() {
                    public List<NotificationLog> run() {
                        return delegate.getNotifications(patientId);
                    }
                });
    }

    private void check(String action, String details) {
        if (!isAllowed(action)) {
            log.record(LocalDateTime.now(), user.getUserId(), user.getName(),
                    user.getRole(), action, details, false);
            throw new SecurityException("Access denied for " + user.getName()
                    + " (" + user.getRole() + ") to " + action + ".");
        }
        log.record(LocalDateTime.now(), user.getUserId(), user.getName(),
                user.getRole(), action, details, true);
    }

    private <T> T call(String action, String details, Call<T> call) {
        check(action, details);
        return call.run();
    }

    private boolean isAllowed(String action) {
        String feature = featureFor(action);
        if ("generateReport".equals(action)) {
            return user.canAccess(User.REPORTS) || user.canAccess(User.VIEW_REPORTS);
        }
        return user.canAccess(feature);
    }

    private String featureFor(String action) {
        if ("findPatient".equals(action) || "registerPatient".equals(action)
                || "addAllergy".equals(action) || "recordVisit".equals(action)
                || "createGuardianNotification".equals(action)
                || "updateNotificationStatus".equals(action)
                || "getNotifications".equals(action)) {
            return User.RECORD_TRANSACTION;
        }
        if ("getVisitHistory".equals(action) || "searchRecords".equals(action)
                || "detectOutbreaks".equals(action)
                || "detectRecurringVisit".equals(action)
                || "getAllAlerts".equals(action)) {
            return User.RECORDS;
        }
        if ("checkSafety".equals(action) || "dispenseMedicine".equals(action)) {
            return User.DISPENSE;
        }
        if ("addMedicine".equals(action) || "restockMedicine".equals(action)) {
            return User.RESTOCK;
        }
        if ("getStock".equals(action) || "getLowStock".equals(action)
                || "getExpiringWithin".equals(action)
                || "estimateDaysRemaining".equals(action)) {
            return User.VIEW_STOCK;
        }
        if ("generateReport".equals(action)) {
            return User.REPORTS;
        }
        throw new IllegalArgumentException("Unknown clinic action: " + action);
    }

    private interface Call<T> {
        T run();
    }
}
