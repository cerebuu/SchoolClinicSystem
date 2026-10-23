package clinic.service;
import clinic.core.ClinicSystem;
import clinic.model.*;
import java.time.*;
import java.util.*;
public final class InMemoryClinicSystem implements ClinicSystem {
    private final Map<String, Patient> patients=new LinkedHashMap<>();
    private final List<MedicalRecord> records=new ArrayList<>();
    private final Map<String, MedicineTransaction> transactions=new LinkedHashMap<>();
    private final Map<String, NotificationLog> notifications=new LinkedHashMap<>();
    private final Inventory inventory=new Inventory();
    private final SafetyChecker safety=new SafetyChecker();
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
        String warning=safety.generateWarning(patient(patient), medicine);
        return warning.isEmpty()?List.of():List.of(warning);
    }
    public synchronized MedicineTransaction dispenseMedicine(String patient, String medicine, int quantity, String staff, boolean override) {
        patient(patient);
        clinic.util.Validation.required(staff, "Staff");
        if(!checkSafety(patient, medicine).isEmpty()&&!override)throw new IllegalStateException("Safety confirmation is required.");
        LocalDate expiry=inventory.dispense(medicine, quantity);
        MedicineTransaction t=new MedicineTransaction(id(), medicine, patient, staff, quantity, expiry, override?"Safety override":"");
        transactions.put(t.getTransactionId(), t);
        return t;
    }
    public synchronized void addMedicine(String name, int quantity, LocalDate expiry, String remarks) {
        clinic.util.Validation.positive(quantity);
        inventory.addMedicine(new Medicine(name, quantity, expiry, LocalDate.now(), remarks));
    }
    public synchronized void restockMedicine(String name, int quantity, LocalDate expiry) {
        inventory.restockMedicine(name, quantity, expiry);
    }
    public synchronized int getStock(String name) {
        return inventory.getStock(name);
    }
    public synchronized List<Medicine> getLowStock() {
        return inventory.getLowStock(10);
    }
    public synchronized List<Medicine> getExpiringWithin(int days) {
        return inventory.getExpiringWithin(days);
    }
    public synchronized double estimateDaysRemaining(String name) {
        RestockForecast f=new RestockForecast();
        return f.estimateDaysRemaining(getStock(name), f.calculateUsageRate(name, new ArrayList<>(transactions.values()), 30));
    }
    public synchronized List<Alert> detectOutbreaks() {
        return new PatternDetector().detectOutbreak(records, new ArrayList<>(patients.values()), 7, 3);
    }
    public synchronized Optional<Alert> detectRecurringVisit(String id) {
        patient(id);
        return Optional.ofNullable(new PatternDetector().detectRecurringVisit(records, id, 30, 3));
    }
    public synchronized List<Alert> getAllAlerts() {
        List<Alert> result=new ArrayList<>(detectOutbreaks());
        getLowStock().forEach(m->result.add(new Alert("LOW_STOCK", m.getName())));
        getExpiringWithin(30).forEach(m->result.add(new Alert("EXPIRING", m.getName())));
        return result;
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
        patient(patient);
        MedicineTransaction t=transactions.get(transaction);
        if(t==null||!t.getPatientId().equals(patient))throw new IllegalArgumentException("Transaction does not belong to patient.");
        NotificationLog n=new NotificationLog(id(), patient, transaction, reason, LocalDateTime.now());
        notifications.put(n.getNotificationId(), n);
        return n;
    }
    public synchronized void updateNotificationStatus(String id, NotificationStatus status) {
        NotificationLog n=notifications.get(id);
        if(n==null||status!=NotificationStatus.SENT)throw new IllegalArgumentException("Select a pending notification to mark sent.");
        n.markSent();
    }
    public synchronized List<NotificationLog> getNotifications(String patient) {
        patient(patient);
        return notifications.values().stream().filter(n->n.getPatientId().equals(patient)).toList();
    }
    // Bounded query gateway: scans stored data but copies only the requested page.
    public synchronized PageResult<Patient> patientPage(int page, int size, String filter) {
        PageResult<Patient> result = PageResult.select(patients.values(), page, size, p -> clinic.util.PatientDisplay.matchesSearch(p, filter));
        return new PageResult<>(result.rows().stream().map(Patient::copy).toList(), result.total());
    }
    public synchronized PageResult<Medicine> medicinePage(int page, int size, String filter) {
        return inventory.page(page, size, filter);
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
        return PageResult.select(notifications.values(), page, size, n->matches(n.getPatientId(), filter));
    }
    public synchronized PageResult<Alert> alertPage(int page, int size, String query) {
        List<Alert> selected=new ArrayList<>();
        int[] total= {
            0
        };
        String q=query.toLowerCase(Locale.ROOT);
        java.util.function.Consumer<Alert> collect=a-> {
            if(a.toString().toLowerCase(Locale.ROOT).contains(q)) {
                if(total[0]>=(long)page*size&&selected.size()<size)selected.add(a);
                total[0]++;
            }
        };
        new PatternDetector().outbreakStream(records, new ArrayList<>(patients.values()), 7, 3).forEach(collect);
        inventory.collectAlerts(collect);
        return new PageResult<>(selected, total[0]);
    }
    public synchronized int patientCount() {
        return patients.size();
    }
    public synchronized int recordCount() {
        return records.size();
    }
}
