package clinic.service;
import clinic.core.ClinicSystem;
import clinic.model.*;
import java.time.*;
import java.util.*;
public final class InMemoryClinicSystem implements ClinicData {
    private final Map<String, Patient> patients=new LinkedHashMap<>();
    private final List<MedicalRecord> records=new ArrayList<>();
    private final Map<String, MedicineTransaction> transactions=new LinkedHashMap<>();
    private final List<InventoryMovement> inventoryMovements=new ArrayList<>();
    private final NotificationStore notifications=new NotificationStore();
    private final Inventory inventory=new Inventory();
    private final SafetyChecker safety=new SafetyChecker();
    public InMemoryClinicSystem() {}
    /** Rehydrates a transaction-local domain aggregate; never used as durable storage. */
    public InMemoryClinicSystem(State state) {
        this(state,state.inventoryMovements());
    }
    private InMemoryClinicSystem(State state,List<InventoryMovement> movements) {
        state.patients().forEach(p -> patients.put(p.getId(), p.copy()));
        records.addAll(state.records());
        state.transactions().forEach(t -> transactions.put(t.getTransactionId(), t));
        inventoryMovements.addAll(movements);
        notifications.restore(state.notifications());
        inventory.restoreBatches(state.batches());
    }
    public synchronized State snapshot() {
        return new State(patients.values().stream().map(Patient::copy).toList(), List.copyOf(records),
            List.copyOf(transactions.values()), notifications.all(), inventory.batches(),List.copyOf(inventoryMovements));
    }
    public record State(List<Patient> patients, List<MedicalRecord> records,
        List<MedicineTransaction> transactions, List<NotificationLog> notifications, List<Medicine> batches,
        List<InventoryMovement> inventoryMovements) {
        public State(List<Patient> patients,List<MedicalRecord> records,List<MedicineTransaction> transactions,
                List<NotificationLog> notifications,List<Medicine> batches) {
            this(patients,records,transactions,notifications,batches,List.of());
        }
    }
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
    public synchronized void updatePatient(String id, Patient profile) {
        String patientId=clinic.util.Validation.required(id,"Patient ID");
        Patient current=patient(patientId);
        patients.put(patientId,current.withUpdatedProfile(profile));
    }
    public synchronized void addAllergy(String id, String allergy) {
        patient(id).addAllergy(allergy);
    }
    public synchronized MedicalRecord recordVisit(String patient, List<String> symptoms, String reason, String remarks, String staff) {
        return recordVisitWithCampus(patient,symptoms,reason,remarks,staff,null);
    }
    @Override
    public synchronized MedicalRecord recordVisitWithCampus(String patient,List<String> symptoms,String reason,
            String remarks,String staff,String campusId) {
        patient(patient);
        MedicalRecord r=new MedicalRecord(id(),patient,symptoms,reason,remarks,"",staff,LocalDateTime.now(),campusId);
        records.add(r);
        return r;
    }
    public synchronized MedicalRecord recordVisitAndAdminister(String patientId,List<String> symptoms,
            String reason,String remarks,String staff,List<DispenseItem> requested,boolean allergyConfirmed) {
        return recordVisitAndAdministerWithCampus(patientId,symptoms,reason,remarks,staff,requested,allergyConfirmed,null);
    }
    @Override
    public synchronized MedicalRecord recordVisitAndAdministerWithCampus(String patientId,List<String> symptoms,
            String reason,String remarks,String staff,List<DispenseItem> requested,boolean allergyConfirmed,String campusId) {
        return recordVisitAndAdministerWithReason(patientId,symptoms,reason,remarks,staff,requested,
            allergyConfirmed?"Allergies reviewed by staff":"",campusId);
    }
    @Override
    public synchronized MedicalRecord recordVisitAndAdministerWithReason(String patientId,List<String> symptoms,
            String reason,String remarks,String staff,List<DispenseItem> requested,String overrideReason,String campusId) {
        Patient profile=patient(patientId);
        clinic.util.Validation.required(staff,"Staff");
        String writtenReason=overrideReason==null?"":overrideReason.trim();
        List<DispenseItem> items=requested==null||requested.isEmpty()?List.of():DispenseWorkflow.merge(requested);
        Map<String,Integer> totals=new LinkedHashMap<>();
        for(DispenseItem item:items) {
            List<String> safetyWarnings=checkSafety(patientId,item.medicine());
            if(safetyWarnings.stream().anyMatch(w->!w.equals(SafetyChecker.ALLERGY_CONFIRMATION)))
                throw new IllegalStateException(String.join("; ",safetyWarnings));
            if(safetyWarnings.contains(SafetyChecker.ALLERGY_CONFIRMATION)&&writtenReason.length()<10)
                throw new IllegalStateException(SafetyChecker.ALLERGY_CONFIRMATION);
            MedicineProduct product=item.product();
            totals.merge(product.key(),item.quantity(),Math::addExact);
            int available=inventory.getStock(product);
            if(item.quantity()>available)
                throw new IllegalStateException("Only "+available+" left in stock for "+product.displayName()
                    +". Nothing was recorded or dispensed.");
        }
        for(DispenseItem item:items) {
            int available=inventory.getStock(item.product());
            if(totals.get(item.product().key())>available)
                throw new IllegalStateException("Only "+available+" left in stock for "+item.product().displayName()
                    +". Nothing was recorded or dispensed.");
        }
        if(campusId!=null&&!campusId.isBlank())clinic.util.Validation.required(campusId,"Campus ID");
        String visitId=id();
        String medication=items.stream().map(DispenseItem::toString).collect(java.util.stream.Collectors.joining(", "));
        MedicalRecord visit=new MedicalRecord(visitId,patientId,symptoms,reason,remarks,medication,staff,
            LocalDateTime.now(),campusId);
        records.add(visit);
        for(DispenseItem item:items)
            DispensingService.dispense(this,inventory,transactions,patientId,item.product(),item.quantity(),
                staff,writtenReason,visitId,campusId);
        return visit;
    }
    public synchronized List<MedicalRecord> getVisitHistory(String patient) {
        patient(patient);
        return records.stream().filter(r->r.getPatientId().equals(patient)).toList();
    }
    public synchronized List<MedicalRecord> getVisitHistory(String patient,String campusId) {
        patient(patient);
        return records.stream().filter(r->r.getPatientId().equals(patient)
            &&(campusId==null||campusId.equals(r.getCampusId()))).toList();
    }
    public synchronized List<String> checkSafety(String patient, String medicine) {
        return safety.check(patients.get(patient), medicine, inventory);
    }
    public synchronized MedicineTransaction dispenseMedicine(String patient, String medicine, int quantity, String staff, boolean override) {
        return dispenseMedicine(patient, medicine, quantity, staff,
            override ? "Allergies reviewed by staff" : "");
    }
    public synchronized MedicineTransaction dispenseMedicine(String patient, String medicine, int quantity, String staff, String overrideReason) {
        return DispensingService.dispense(this, inventory, transactions, patient,
            new MedicineProduct(medicine,"",""), quantity, staff, overrideReason,null,null);
    }
    public synchronized MedicineTransaction dispenseMedicine(String patient,MedicineProduct medicine,int quantity,
            String staff,boolean overrideConfirmed) {
        return DispensingService.dispense(this,inventory,transactions,patient,medicine,quantity,staff,
            overrideConfirmed?"Allergies reviewed by staff":"",null,null);
    }
    @Override
    public synchronized MedicineTransaction dispenseMedicineWithCampus(String patient,MedicineProduct medicine,int quantity,
            String staff,String overrideReason,String campusId) {
        return DispensingService.dispense(this,inventory,transactions,patient,medicine,quantity,staff,
            overrideReason,null,campusId);
    }
    public synchronized void addMedicine(String name, int quantity, LocalDate expiry, String remarks) {
        clinic.util.Validation.positive(quantity);
        inventory.addMedicine(new Medicine(name, quantity, expiry, LocalDate.now(), remarks));
    }
    public synchronized void addMedicine(MedicineProduct medicine,int quantity,LocalDate expiry,String remarks) {
        clinic.util.Validation.positive(quantity);
        inventory.addMedicine(new Medicine(medicine,quantity,expiry,LocalDate.now(),remarks));
    }
    public synchronized void restockMedicine(String name, int quantity, LocalDate expiry) {
        inventory.restockMedicine(name, quantity, expiry);
    }
    public synchronized void restockMedicine(String name, int quantity, LocalDate expiry, String remarks) {
        inventory.restockMedicine(name, quantity, expiry, remarks);
    }
    public synchronized void restockMedicine(MedicineProduct product,int quantity,LocalDate expiry,String remarks) {
        inventory.restockMedicine(product,quantity,expiry,remarks);
    }
    public synchronized List<Medicine> matchingMedicineBatches(String name,LocalDate expiry) {
        return inventory.matchingBatches(name,expiry);
    }
    public synchronized List<Medicine> matchingMedicineBatches(MedicineProduct product,LocalDate expiry) {
        return inventory.matchingBatches(product,expiry);
    }
    public synchronized void mergeMedicineBatch(String name,int quantity,LocalDate expiry,String remarks) {
        inventory.mergeBatch(name,quantity,expiry,remarks);
    }
    public synchronized void mergeMedicineBatch(MedicineProduct product,int quantity,LocalDate expiry,String remarks) {
        inventory.mergeBatch(product,quantity,expiry,remarks);
    }
    public synchronized void mergeDuplicateMedicineBatches(String name,LocalDate expiry) {
        inventory.mergeDuplicateBatches(name,expiry);
    }
    public synchronized void mergeDuplicateMedicineBatches(MedicineProduct product,LocalDate expiry) {
        inventory.mergeDuplicateBatches(product,expiry);
    }
    public synchronized void refillBatch(String batchId,int quantity,LocalDate expiry,String remarks) {
        inventory.refillBatch(batchId,quantity,expiry,remarks);
    }
    public synchronized void saveInventoryBatch(MedicineProduct product,int quantity,LocalDate expiry,String remarks,
            boolean restock,boolean merge,String actorId,String campusId) {
        InventoryMovement.Type type=restock?InventoryMovement.Type.RESTOCK:InventoryMovement.Type.INITIAL_STOCK;
        InventoryMovement prepared=new InventoryMovement(null,type,product,quantity,expiry,null,
            LocalDateTime.now(),actorId,campusId);
        String batchId=null;
        if(merge) {
            inventory.mergeBatch(product,quantity,expiry,remarks);
            batchId=BatchMatcher.find(inventory.batches(),product,expiry).map(match->match.batch().getBatchId()).orElse(null);
        } else if(restock) {
            int priorBatchCount=inventory.batches().size();
            inventory.restockMedicine(product,quantity,expiry,remarks);
            List<Medicine> updatedBatches=inventory.batches();
            if(updatedBatches.size()>priorBatchCount)batchId=updatedBatches.get(priorBatchCount).getBatchId();
        } else {
            Medicine batch=new Medicine(product,quantity,expiry,LocalDate.now(),remarks);
            inventory.addMedicine(batch);
            batchId=batch.getBatchId();
        }
        inventoryMovements.add(new InventoryMovement(prepared.id(),type,product,quantity,expiry,batchId,
            prepared.dateTime(),prepared.actorId(),prepared.campusId()));
    }
    public synchronized void refillInventoryBatch(String batchId,int quantity,LocalDate expiry,String remarks,
            String actorId,String campusId) {
        Medicine batch=inventory.batches().stream().filter(item->item.getBatchId().equals(batchId)).findFirst()
            .orElseThrow(()->new IllegalArgumentException("Unknown batch ID."));
        InventoryMovement prepared=new InventoryMovement(null,InventoryMovement.Type.REFILL,batch.getProduct(),
            quantity,expiry,batchId,LocalDateTime.now(),actorId,campusId);
        inventory.refillBatch(batchId,quantity,expiry,remarks);
        inventoryMovements.add(prepared);
    }
    public synchronized int getStock(String name) {
        return inventory.getStock(name);
    }
    public synchronized int getStock(MedicineProduct product) {
        return inventory.getStock(product);
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
    public synchronized List<Alert> detectOutbreaks(String campusId) {
        return new PatternDetector().detectOutbreak(recordsForCampus(campusId),new ArrayList<>(patients.values()),
            FeatureRules.OUTBREAK_DAYS,FeatureRules.VISIT_THRESHOLD);
    }
    public synchronized Optional<Alert> detectRecurringVisit(String id) {
        return Optional.ofNullable(new PatternDetector().detectRecurringVisit(records, patients.containsKey(id)?id:null, FeatureRules.RECURRING_DAYS, FeatureRules.VISIT_THRESHOLD));
    }
    public synchronized Optional<Alert> detectRecurringVisit(String id,String campusId) {
        return Optional.ofNullable(new PatternDetector().detectRecurringVisit(recordsForCampus(campusId),
            patients.containsKey(id)?id:null,FeatureRules.RECURRING_DAYS,FeatureRules.VISIT_THRESHOLD));
    }
    public synchronized List<Alert> getAllAlerts() {
        return AlertCollector.collect(records, patients.values(), inventory);
    }
    public synchronized List<Alert> getAllAlerts(String campusId) {
        return AlertCollector.collect(recordsForCampus(campusId),patients.values(),inventory);
    }
    public String generateReport(clinic.model.Formattable item) {
        return new RecordFormatter().generateReport(item);
    }
    public synchronized List<MedicalRecord> searchRecords(String patient, String symptom, String medicine, LocalDate date, String section, String department) {
        return searchRecords(patient,symptom,medicine,date,section,department,null);
    }
    public synchronized List<MedicalRecord> searchRecords(String patient,String symptom,String medicine,LocalDate date,
            String section,String department,String campusId) {
        return records.stream()
            .filter(r->campusId==null||campusId.equals(r.getCampusId()))
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
                    (campusId==null||campusId.equals(transaction.getCampusId()))
                    &&transaction.getPatientId().equals(r.getPatientId())
                    &&r.getRecordId().equals(transaction.getVisitId())
                    && matches(transaction.getMedicineName(), medicine)))
            .toList();
    }
    private boolean matches(String value, String filter) {
        return filter==null||filter.isBlank()||value.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }
    public synchronized NotificationLog createGuardianNotification(String patient, String transaction, String reason) {
        return notifications.create(patients.get(patient), transactions.get(transaction), reason);
    }
    @Override
    public synchronized NotificationLog createGuardianNotificationWithCampus(String patientId,String transactionId,
            String reason,String campusId) {
        String campus=clinic.util.Validation.required(campusId,"Campus ID");
        return notifications.create(patients.get(patientId),transactions.get(transactionId),reason,campus);
    }
    public synchronized void updateNotificationStatus(String id, NotificationStatus status) {
        notifications.update(id, status);
    }
    @Override
    public synchronized void updateNotificationStatusForCampus(String id,NotificationStatus status,String campusId) {
        notifications.update(id,status,campusId);
    }
    public synchronized List<NotificationLog> getNotifications(String patient) {
        return notifications.forPatient(patients.get(patient));
    }
    @Override
    public synchronized List<NotificationLog> getNotificationsForCampus(String patientId,String campusId) {
        return notifications.forPatient(patients.get(patientId),campusId);
    }
    public synchronized NotificationLog createEmergencyNotification(String patientId) {
        return notifications.createEmergency(patient(patientId));
    }
    @Override
    public synchronized NotificationLog createEmergencyNotificationWithCampus(String patientId,String campusId) {
        return notifications.createEmergency(patient(patientId),
            clinic.util.Validation.required(campusId,"Campus ID"));
    }
    public synchronized NotificationLog beginNotificationAttempt(String id,clinic.notify.Channel channel,
            LocalDateTime attemptedAt) {
        return notifications.beginAttempt(id,channel,attemptedAt);
    }
    @Override
    public synchronized NotificationLog beginNotificationAttemptForCampus(String id,clinic.notify.Channel channel,
            LocalDateTime attemptedAt,String campusId) {
        return notifications.beginAttempt(id,channel,attemptedAt,campusId);
    }
    public synchronized void completeNotificationAttempt(String id,NotificationStatus status) {
        notifications.completeAttempt(id,status);
    }
    @Override
    public synchronized void completeNotificationAttemptForCampus(String id,NotificationStatus status,String campusId) {
        notifications.completeAttempt(id,status,campusId);
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
    public synchronized PageResult<MedicineTransaction> transactionPage(int page,int size,String query,
            LocalDateTime from,LocalDateTime to,String campusId) {
        return DispenseQueries.transactions(transactions.values(),page,size,query,from,to,campusId);
    }
    public synchronized PageResult<MedicationHistoryEntry> medicationHistoryPage(int page,int requestedSize,
            String query,LocalDateTime from,LocalDateTime to,String campusId) {
        int size=DispenseQueries.size(requestedSize);
        if(page<0)throw new IllegalArgumentException("Invalid page.");
        String filter=DispenseQueries.query(query);
        List<MedicationHistoryEntry> filtered=new ArrayList<>();
        for(MedicineTransaction transaction:transactions.values()) {
            MedicationHistoryEntry entry=MedicationHistoryEntry.from(transaction);
            if(matchesHistory(entry,filter,from,to,campusId))filtered.add(entry);
        }
        for(InventoryMovement movement:inventoryMovements) {
            MedicationHistoryEntry entry=MedicationHistoryEntry.from(movement);
            if(matchesHistory(entry,filter,from,to,campusId))filtered.add(entry);
        }
        filtered.sort(Comparator.comparing(MedicationHistoryEntry::dateTime).reversed()
            .thenComparing(MedicationHistoryEntry::id,Comparator.reverseOrder()));
        long start=(long)page*size;
        List<MedicationHistoryEntry> rows=start>=filtered.size()?List.of():filtered.subList((int)start,
            (int)Math.min(start+size,filtered.size()));
        return new PageResult<>(rows,filtered.size(),page,size);
    }
    private static boolean matchesHistory(MedicationHistoryEntry entry,String query,LocalDateTime from,
            LocalDateTime to,String campusId) {
        if(campusId!=null&&!campusId.equals(entry.campusId()))return false;
        if(from!=null&&entry.dateTime().isBefore(from)||to!=null&&!entry.dateTime().isBefore(to))return false;
        if(query.isEmpty())return true;
        String text=(entry.id()+" "+entry.type()+" "+entry.medicine().displayName()+" "+entry.actorId()+" "+
            (entry.patientId()==null?"":entry.patientId())+" "+(entry.campusId()==null?"":entry.campusId())+" "+
            (entry.batchId()==null?"":entry.batchId())).toLowerCase(Locale.ROOT);
        return text.contains(query);
    }
    public synchronized PageResult<MedicalRecord> recordPage(int page, int size, String filter) {
        return PageResult.select(records, page, size, r->matches(r.getPatientId(), filter)||matches(r.getReason(), filter));
    }
    public synchronized PageResult<MedicalRecord> recordPage(int page,int size,String filter,String campusId) {
        return PageResult.select(records,page,size,r->(campusId==null||campusId.equals(r.getCampusId()))
            &&(matches(r.getPatientId(),filter)||matches(r.getReason(),filter)));
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
    public synchronized PageResult<MedicalRecord> recentRecordPage(int page,int size,String query,String staffId,String campusId) {
        Iterable<MedicalRecord> newestFirst=() -> new Iterator<>() {
            private int index=records.size()-1;
            public boolean hasNext(){return index>=0;}
            public MedicalRecord next(){if(!hasNext())throw new NoSuchElementException();return records.get(index--);}
        };
        return PageResult.select(newestFirst,page,size,record->(staffId==null||staffId.equals(record.getStaffId()))
            &&(campusId==null||campusId.equals(record.getCampusId()))
            &&(matches(record.getPatientId(),query)||matches(record.getReason(),query)));
    }
    public synchronized PageResult<MedicalRecord> recentRecordPage(int page, int size, String nameOrId,
            String staffId, LocalDateTime fromInclusive, LocalDateTime toInclusive) {
        return VisitSearch.page(records, patients, page, size, nameOrId, staffId, fromInclusive, toInclusive);
    }
    public synchronized PageResult<MedicalRecord> recentRecordPage(int page,int size,String nameOrId,
            String staffId,LocalDateTime fromInclusive,LocalDateTime toInclusive,String campusId) {
        if(toInclusive==null||fromInclusive!=null&&fromInclusive.isAfter(toInclusive))
            throw new IllegalArgumentException("A valid visit date range is required.");
        String needle=nameOrId==null?"":nameOrId.trim().toLowerCase(Locale.ROOT);
        Iterable<MedicalRecord> newest=() -> new Iterator<>() {
            private int index=records.size()-1;
            public boolean hasNext(){return index>=0;}
            public MedicalRecord next(){if(!hasNext())throw new NoSuchElementException();return records.get(index--);}
        };
        return PageResult.select(newest,page,size,visit->{
            if(staffId!=null&&!staffId.equals(visit.getStaffId())
                    ||campusId!=null&&!campusId.equals(visit.getCampusId()))return false;
            LocalDateTime date=visit.getVisitDateTime();
            if(date==null||date.isAfter(toInclusive)||fromInclusive!=null&&date.isBefore(fromInclusive))return false;
            Patient patient=patients.get(visit.getPatientId());
            return needle.isEmpty()||visit.getPatientId().toLowerCase(Locale.ROOT).contains(needle)
                ||patient!=null&&patient.getName().toLowerCase(Locale.ROOT).contains(needle);
        });
    }
    public synchronized PageResult<NotificationLog> notificationPage(int page, int size, String filter) {
        return notifications.page(page, size, filter);
    }
    @Override
    public synchronized PageResult<NotificationLog> notificationPageForCampus(int page,int size,String filter,
            String campusId) {
        return notifications.page(page,size,filter,campusId);
    }
    public synchronized PageResult<Alert> alertPage(int page, int size, String query) {
        return AlertCollector.page(getAllAlerts(), page, size, query);
    }
    public synchronized PageResult<Alert> alertPage(int page,int size,String query,String campusId) {
        return AlertCollector.page(getAllAlerts(campusId),page,size,query);
    }
    public synchronized int patientCount() {
        return patients.size();
    }
    public synchronized int recordCount() {
        return records.size();
    }
    public synchronized int recordCount(String campusId) {
        return campusId==null?records.size():(int)records.stream().filter(record->campusId.equals(record.getCampusId())).count();
    }
    private List<MedicalRecord> recordsForCampus(String campusId) {
        return campusId==null?List.copyOf(records):records.stream()
            .filter(record->campusId.equals(record.getCampusId())).toList();
    }
}
