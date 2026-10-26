package clinic.service;
import clinic.core.ClinicSystem;
import clinic.model.*;
import java.time.*;
import java.util.*;
public final class InMemoryClinicSystem implements ClinicData {
    private final Map<String, Patient> patients=new LinkedHashMap<>();
    private final List<MedicalRecord> records=new ArrayList<>();
    private final Map<String, MedicineTransaction> transactions=new LinkedHashMap<>();
    private final NotificationStore notifications=new NotificationStore();
    private final Inventory inventory=new Inventory();
    private final SafetyChecker safety=new SafetyChecker();
    public InMemoryClinicSystem() {}
    /** Rehydrates a transaction-local domain aggregate; never used as durable storage. */
    public InMemoryClinicSystem(State state) {
        state.patients().forEach(p -> patients.put(p.getId(), p.copy()));
        records.addAll(state.records());
        state.transactions().forEach(t -> transactions.put(t.getTransactionId(), t));
        notifications.restore(state.notifications());
        inventory.restoreBatches(state.batches());
    }
    public synchronized State snapshot() {
        return new State(patients.values().stream().map(Patient::copy).toList(), List.copyOf(records),
            List.copyOf(transactions.values()), notifications.all(), inventory.batches());
    }
    public record State(List<Patient> patients, List<MedicalRecord> records,
        List<MedicineTransaction> transactions, List<NotificationLog> notifications, List<Medicine> batches) {}
    private String id() {
        return UUID.randomUUID().toString();
    }
    private Patient patient(String id) {
        Patient p=patients.get(id);
        if(p==null)throw new IllegalArgumentException("Patient not found.");
        return p;
    }
    public synchronized Optional<Patient> findPatient(String id) {
        return Optional.ofNullable(patients.get(id)).map(Patient::copy);
    }
    public synchronized void registerPatient(Patient p) {
        Objects.requireNonNull(p);
        if(patients.putIfAbsent(p.getId(), p.copy())!=null)throw new IllegalArgumentException("Patient ID already exists.");
    }
    public synchronized void addAllergy(String id, String allergy) {
        patient(id).addAllergy(allergy);
    }
    public synchronized MedicalRecord recordVisit(String patient, List<String> symptoms, String reason, String remarks, String staff) {
        patient(patient);
        MedicalRecord r=new MedicalRecord(id(), patient, symptoms, reason, remarks, "", staff);
        records.add(r);
        return r;
    }
    public synchronized List<MedicalRecord> getVisitHistory(String patient) {
        patient(patient);
        return records.stream().filter(r->r.getPatientId().equals(patient)).toList();
    }
    public synchronized List<String> checkSafety(String patient, String medicine) {
        return safety.check(patients.get(patient), medicine, inventory);
    }
    public synchronized MedicineTransaction dispenseMedicine(String patient, String medicine, int quantity, String staff, boolean override) {
        return dispenseMedicine(patient, medicine, quantity, staff,
            override ? "Allergies reviewed by staff" : "");
    }
    public synchronized MedicineTransaction dispenseMedicine(String patient, String medicine, int quantity, String staff, String overrideReason) {
        return DispensingService.dispense(this, inventory, transactions, patient, medicine, quantity, staff, overrideReason);
    }
    public synchronized void addMedicine(String name, int quantity, LocalDate expiry, String remarks) {
        clinic.util.Validation.positive(quantity);
        inventory.addMedicine(new Medicine(name, quantity, expiry, LocalDate.now(), remarks));
    }
    public synchronized void restockMedicine(String name, int quantity, LocalDate expiry) {
        inventory.restockMedicine(name, quantity, expiry);
    }
    public synchronized void restockMedicine(String name, int quantity, LocalDate expiry, String remarks) {
        inventory.restockMedicine(name, quantity, expiry, remarks);
    }
    public synchronized List<Medicine> matchingMedicineBatches(String name,LocalDate expiry) {
        return inventory.matchingBatches(name,expiry);
    }
    public synchronized void mergeMedicineBatch(String name,int quantity,LocalDate expiry,String remarks) {
        inventory.mergeBatch(name,quantity,expiry,remarks);
    }
    public synchronized void mergeDuplicateMedicineBatches(String name,LocalDate expiry) {
        inventory.mergeDuplicateBatches(name,expiry);
    }
    public synchronized void refillBatch(String batchId,int quantity,LocalDate expiry,String remarks) {
        inventory.refillBatch(batchId,quantity,expiry,remarks);
    }
    public synchronized int getStock(String name) {
        return inventory.getStock(name);
    }
    public synchronized List<Medicine> getLowStock() {
        return inventory.getLowStock(FeatureRules.LOW_STOCK_THRESHOLD);
    }
    public synchronized List<Medicine> getExpiringWithin(int days) {
        return inventory.getExpiringWithin(days);
    }
    public synchronized double estimateDaysRemaining(String name) {
        return new RestockForecast().estimate(name, inventory, new ArrayList<>(transactions.values()));
    }
    public synchronized List<Alert> detectOutbreaks() {
        return new PatternDetector().detectOutbreak(records, new ArrayList<>(patients.values()), FeatureRules.OUTBREAK_DAYS, FeatureRules.VISIT_THRESHOLD);
    }
    public synchronized Optional<Alert> detectRecurringVisit(String id) {
        return Optional.ofNullable(new PatternDetector().detectRecurringVisit(records, patients.containsKey(id)?id:null, FeatureRules.RECURRING_DAYS, FeatureRules.VISIT_THRESHOLD));
    }
    public synchronized List<Alert> getAllAlerts() {
        return AlertCollector.collect(records, patients.values(), inventory);
    }
    public String generateReport(clinic.model.Formattable item) {
        return new RecordFormatter().generateReport(item);
    }
    public synchronized List<MedicalRecord> searchRecords(String patient, String symptom, String medicine, LocalDate date, String section, String department) {
        return records.stream()
            .filter(r -> matches(r.getPatientId(), patient))
            .filter(r -> symptom == null || symptom.isBlank()
                || r.getSymptoms().stream().anyMatch(value -> matches(value, symptom)))
            .filter(r -> date == null || r.getVisitDateTime().toLocalDate().equals(date))
            .filter(r -> section == null || section.isBlank()
                || patients.get(r.getPatientId()) instanceof Student student
                    && matches(student.getSection(), section))
            .filter(r -> department == null || department.isBlank()
                || patients.get(r.getPatientId()) instanceof Employee employee
                    && matches(employee.getDepartment(), department))
            .filter(r -> medicine == null || medicine.isBlank()
                || transactions.values().stream().anyMatch(transaction ->
                    transaction.getPatientId().equals(r.getPatientId())
                    && transaction.getDateTime().toLocalDate().equals(r.getVisitDateTime().toLocalDate())
                    && matches(transaction.getMedicineName(), medicine)))
            .toList();
    }
    private boolean matches(String value, String filter) {
        return filter==null||filter.isBlank()||value.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }
    public synchronized NotificationLog createGuardianNotification(String patient, String transaction, String reason) {
        return notifications.create(patients.get(patient), transactions.get(transaction), reason);
    }
    public synchronized void updateNotificationStatus(String id, NotificationStatus status) {
        notifications.update(id, status);
    }
    public synchronized List<NotificationLog> getNotifications(String patient) {
        return notifications.forPatient(patients.get(patient));
    }
    // Bounded query gateway: scans stored data but copies only the requested page.
    public synchronized PageResult<Patient> patientPage(int page, int size, String filter) {
        PageResult<Patient> result = PageResult.select(patients.values(), page, size, p -> clinic.util.PatientDisplay.matchesSearch(p, filter));
        return new PageResult<>(result.rows().stream().map(Patient::copy).toList(), result.total());
    }
    public synchronized PageResult<Medicine> medicinePage(int page, int size, String filter) {
        return inventory.page(page, size, filter);
    }
    public synchronized PageResult<Medicine> dispensableMedicinePage(int page,int size,String query) {
        return DispenseQueries.medicines(inventory.batches(),page,size,query);
    }
    public synchronized PageResult<MedicineTransaction> transactionPage(int page,int size,String query,LocalDateTime from,LocalDateTime to) {
        return DispenseQueries.transactions(transactions.values(),page,size,query,from,to);
    }
    public synchronized PageResult<MedicalRecord> recordPage(int page, int size, String filter) {
        return PageResult.select(records, page, size, r->matches(r.getPatientId(), filter)||matches(r.getReason(), filter));
    }
    /** Newest-first traversal allocates only the requested page, not a sorted copy of all visits. */
    public synchronized PageResult<MedicalRecord> recentRecordPage(int page, int size, String query, String staffId) {
        Iterable<MedicalRecord> newestFirst = () -> new Iterator<>() {
            private int index = records.size() - 1;
            public boolean hasNext() { return index >= 0; }
            public MedicalRecord next() {
                if (!hasNext()) throw new NoSuchElementException();
                return records.get(index--);
            }
        };
        return PageResult.select(newestFirst, page, size, record ->
            (staffId == null || staffId.equals(record.getStaffId()))
                && (matches(record.getPatientId(), query) || matches(record.getReason(), query)));
    }
    public synchronized PageResult<MedicalRecord> recentRecordPage(int page, int size, String nameOrId,
            String staffId, LocalDateTime fromInclusive, LocalDateTime toInclusive) {
        return VisitSearch.page(records, patients, page, size, nameOrId, staffId, fromInclusive, toInclusive);
    }
    public synchronized PageResult<NotificationLog> notificationPage(int page, int size, String filter) {
        return notifications.page(page, size, filter);
    }
    public synchronized PageResult<Alert> alertPage(int page, int size, String query) {
        return AlertCollector.page(getAllAlerts(), page, size, query);
    }
    public synchronized int patientCount() {
        return patients.size();
    }
    public synchronized int recordCount() {
        return records.size();
    }
}
