package clinic.persistence;

import clinic.model.*;
import clinic.service.*;
import java.lang.reflect.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/**
 * Database-authoritative unit of work. Reuses the existing domain rules on a fresh
 * aggregate, then writes only changed rows. No cached clinical state survives a call.
 */
public final class ClinicRepository {
    private static final Set<String> WRITES = Set.of("registerPatient", "addAllergy", "recordVisit",
        "dispenseMedicine", "addMedicine", "restockMedicine", "mergeMedicineBatch", "mergeDuplicateMedicineBatches", "refillBatch",
        "createGuardianNotification", "updateNotificationStatus");
    private ClinicRepository() {}

    public static ClinicData create(Database database) {
        return (ClinicData) Proxy.newProxyInstance(ClinicData.class.getClassLoader(), new Class<?>[]{ClinicData.class}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch(method.getName()) {
                case "toString" -> "Persistent clinic repository";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new IllegalStateException();
            };
            return database.transaction(connection -> {
                ensureBatchIds(connection);
                if(method.getName().equals("dispensableMedicinePage"))return DispenseRepository.medicines(connection,(int)args[0],(int)args[1],(String)args[2]);
                if(method.getName().equals("transactionPage"))return DispenseRepository.transactions(connection,(int)args[0],(int)args[1],(String)args[2],(LocalDateTime)args[3],(LocalDateTime)args[4]);
                if(method.getName().equals("matchingMedicineBatches"))return Sql.query(connection,
                    "SELECT b.*,i.batch_id FROM medicine_batches b JOIN medicine_batch_ids i USING(ordinal) WHERE LOWER(TRIM(b.name))=LOWER(TRIM(?)) AND b.expiry=? AND b.expiry>=? ORDER BY b.batch_date,b.ordinal",
                    r->new Medicine(r.getString("batch_id"),r.getString("name"),r.getInt("quantity"),r.getObject("expiry",LocalDate.class),
                        r.getObject("batch_date",LocalDate.class),r.getString("remarks")),args[0],args[1],LocalDate.now());
                // Frequent dashboard lookups must not materialize unrelated clinical history.
                if(method.getName().equals("patientCount")) return count(connection,"SELECT COUNT(*) FROM patients");
                if(method.getName().equals("recordCount")) return count(connection,"SELECT COUNT(*) FROM visits");
                if(method.getName().equals("findPatient")) return Sql.query(connection,"SELECT * FROM patients WHERE id=?",
                    row -> patient(connection,row),args[0]).stream().findFirst();
                if(method.getName().equals("getStock")) return Sql.query(connection,
                    "SELECT COALESCE(SUM(CAST(quantity AS BIGINT)),0) FROM medicine_batches WHERE LOWER(name)=LOWER(?) AND expiry>=?",
                    row -> Math.toIntExact(row.getLong(1)),args[0],LocalDate.now()).get(0);
                InMemoryClinicSystem.State before = load(connection);
                InMemoryClinicSystem domain = new InMemoryClinicSystem(before);
                Object result;
                try { result = method.invoke(domain, args); }
                catch (InvocationTargetException error) {
                    if (error.getCause() instanceof RuntimeException runtime) throw runtime;
                    throw new IllegalStateException("Clinic operation failed.", error.getCause());
                }
                if (WRITES.contains(method.getName())) saveChanges(connection, before, domain.snapshot());
                return result;
            });
        });
    }

    private static int count(Connection c,String sql) throws SQLException {
        return Sql.query(c,sql,r -> Math.toIntExact(r.getLong(1))).get(0);
    }
    private static void ensureBatchIds(Connection c) throws SQLException {
        List<Integer> missing=Sql.query(c,"SELECT ordinal FROM medicine_batches WHERE ordinal NOT IN (SELECT ordinal FROM medicine_batch_ids) ORDER BY ordinal",r->r.getInt(1));
        for(int ordinal:missing)Sql.update(c,"INSERT INTO medicine_batch_ids(ordinal,batch_id) VALUES(?,?)",ordinal,UUID.randomUUID().toString());
    }
    private static Patient patient(Connection c, ResultSet r) throws SQLException {
        String id=r.getString("id"), name=r.getString("name"), contact=r.getString("contact");
        Patient p = Patient.STUDENT.equals(r.getString("patient_type"))
            ? new Student(id,name,contact,r.getString("course_department"),r.getInt("year_level"),r.getString("section_position"))
            : new Employee(id,name,contact,r.getString("course_department"),r.getString("section_position"));
        for(String allergy:Sql.query(c,"SELECT allergy FROM allergies WHERE patient_id=? ORDER BY allergy", x -> x.getString(1),id)) p.addAllergy(allergy);
        for(String history:Sql.query(c,"SELECT detail FROM patient_history WHERE patient_id=? ORDER BY ordinal", x -> x.getString(1),id)) p.addHistory(history);
        p.setGuardianContact(r.getString("guardian_contact"));
        return p;
    }

    private static InMemoryClinicSystem.State load(Connection c) throws SQLException {
        List<Patient> patients = Sql.query(c,"SELECT * FROM patients ORDER BY id", r -> patient(c,r));
        List<MedicalRecord> visits=Sql.query(c,"SELECT * FROM visits ORDER BY seq",r -> new MedicalRecord(
            r.getString("id"),r.getString("patient_id"),
            Sql.query(c,"SELECT symptom FROM symptoms WHERE visit_id=? ORDER BY ordinal",x -> x.getString(1),r.getString("id")),
            r.getString("reason"),r.getString("assessment"),r.getString("medication"),r.getString("staff_id"),r.getObject("visit_time",LocalDateTime.class)));
        List<Medicine> batches=Sql.query(c,"SELECT b.*,i.batch_id FROM medicine_batches b JOIN medicine_batch_ids i USING(ordinal) ORDER BY b.ordinal",r -> new Medicine(
            r.getString("batch_id"),r.getString("name"),r.getInt("quantity"),r.getObject("expiry",LocalDate.class),r.getObject("batch_date",LocalDate.class),r.getString("remarks")));
        List<MedicineTransaction> transactions=Sql.query(c,"SELECT * FROM dispensing ORDER BY seq",r -> new MedicineTransaction(
            r.getString("id"),r.getString("medicine"),r.getString("patient_id"),r.getString("staff_id"),r.getInt("quantity"),
            r.getObject("expiry",LocalDate.class),r.getString("remarks"),r.getObject("dispensed_at",LocalDateTime.class)));
        List<NotificationLog> notifications=Sql.query(c,"""
            SELECT n.*, p.name AS patient_name, d.medicine FROM notifications n
            JOIN patients p ON p.id=n.patient_id JOIN dispensing d ON d.id=n.transaction_id ORDER BY n.seq
            """,r -> {
                NotificationLog n=new NotificationLog(r.getString("id"),r.getString("patient_id"),r.getString("transaction_id"),
                    r.getString("reason"),r.getObject("created_at",LocalDateTime.class),r.getString("patient_name"),r.getString("medicine"),
                    GuardianMessageTemplates.MessageType.MEDICINE_GIVEN,"the school clinic",r.getString("recipient_label"),r.getString("recipient_contact"));
                if(NotificationStatus.SENT.name().equals(r.getString("status"))) n.markSent();
                return n;
            });
        return new InMemoryClinicSystem.State(patients,visits,transactions,notifications,batches);
    }

    private static void saveChanges(Connection c, InMemoryClinicSystem.State before, InMemoryClinicSystem.State after) throws SQLException {
        Map<String,Patient> oldPatients=new HashMap<>();
        before.patients().forEach(p -> oldPatients.put(p.getId(),p));
        for(Patient p:after.patients()) {
            Patient old=oldPatients.get(p.getId());
            if(old==null) {
                if (!(p instanceof Student) && !(p instanceof Employee)) throw new IllegalArgumentException("Choose a student or employee patient type.");
                Sql.update(c,"INSERT INTO patients(id,name,contact,patient_type,course_department,section_position,year_level,guardian_contact) VALUES(?,?,?,?,?,?,?,?)",p.getId(),p.getName(),p.getContactInfo(),p.getPatientType(),
                    p instanceof Student s?s.getCourse():((Employee)p).getDepartment(),
                    p instanceof Student s?s.getSection():((Employee)p).getPosition(),p instanceof Student s?s.getYearLevel():null,p.getGuardianContact());
                for(int i=0;i<p.getMedicalHistory().size();i++) Sql.update(c,"INSERT INTO patient_history VALUES(?,?,?)",p.getId(),i,p.getMedicalHistory().get(i));
            }
            for(String allergy:p.getAllergies()) if(old==null||!old.hasAllergy(allergy))
                Sql.update(c,"INSERT INTO allergies VALUES(?,?)",p.getId(),allergy);
        }
        Set<String> visits=new HashSet<>();
        before.records().forEach(r -> visits.add(r.getRecordId()));
        for(MedicalRecord r:after.records()) if(!visits.contains(r.getRecordId())) {
            Sql.update(c,"INSERT INTO visits(id,patient_id,reason,assessment,medication,staff_id,visit_time) VALUES(?,?,?,?,?,?,?)",
                r.getRecordId(),r.getPatientId(),r.getReason(),r.getAssessment(),r.getMedicationGiven(),r.getStaffId(),r.getVisitDateTime());
            for(int i=0;i<r.getSymptoms().size();i++) Sql.update(c,"INSERT INTO symptoms VALUES(?,?,?)",r.getRecordId(),i,r.getSymptoms().get(i));
        }
        if(after.batches().size()<before.batches().size()) {
            Sql.update(c,"DELETE FROM medicine_batch_ids");
            Sql.update(c,"DELETE FROM medicine_batches");
            for(int i=0;i<after.batches().size();i++) {
                Medicine batch=after.batches().get(i);
                insertBatch(c,i,batch);
            }
        } else for(int i=0;i<after.batches().size();i++) {
            Medicine batch=after.batches().get(i);
            if(i>=before.batches().size()) insertBatch(c,i,batch);
            else if(batch.getQuantity()!=before.batches().get(i).getQuantity()
                    ||!batch.getRemarks().equals(before.batches().get(i).getRemarks())
                    ||!batch.getExpirationDate().equals(before.batches().get(i).getExpirationDate())
                    ||!batch.getBatchDate().equals(before.batches().get(i).getBatchDate()))
                Sql.update(c,"UPDATE medicine_batches SET quantity=?,expiry=?,batch_date=?,remarks=? WHERE ordinal=?",
                    batch.getQuantity(),batch.getExpirationDate(),batch.getBatchDate(),batch.getRemarks(),i);
        }
        Set<String> transactions=new HashSet<>();
        before.transactions().forEach(t -> transactions.add(t.getTransactionId()));
        for(MedicineTransaction t:after.transactions()) if(!transactions.contains(t.getTransactionId()))
            Sql.update(c,"INSERT INTO dispensing(id,medicine,patient_id,staff_id,quantity,expiry,remarks,dispensed_at) VALUES(?,?,?,?,?,?,?,?)",
                t.getTransactionId(),t.getMedicineName(),t.getPatientId(),t.getStaffId(),t.getQuantity(),t.getExpirationDate(),t.getRemarks(),t.getDateTime());
        Map<String,NotificationStatus> statuses=new HashMap<>();
        before.notifications().forEach(n -> statuses.put(n.getNotificationId(),n.getStatus()));
        for(NotificationLog n:after.notifications()) {
            if(!statuses.containsKey(n.getNotificationId()))
                Sql.update(c,"INSERT INTO notifications(id,patient_id,transaction_id,reason,created_at,status,recipient_label,recipient_contact) VALUES(?,?,?,?,?,?,?,?)",
                    n.getNotificationId(),n.getPatientId(),n.getTransactionId(),n.getReason(),n.getDateTime(),n.getStatus().name(),n.getRecipientLabel(),n.getRecipientContact());
            else if(statuses.get(n.getNotificationId())!=n.getStatus())
                Sql.update(c,"UPDATE notifications SET status=? WHERE id=?",n.getStatus().name(),n.getNotificationId());
        }
    }
    private static void insertBatch(Connection c,int ordinal,Medicine batch) throws SQLException {
        Sql.update(c,"INSERT INTO medicine_batches VALUES(?,?,?,?,?,?)",ordinal,batch.getName(),batch.getQuantity(),batch.getExpirationDate(),batch.getBatchDate(),batch.getRemarks());
        Sql.update(c,"INSERT INTO medicine_batch_ids(ordinal,batch_id) VALUES(?,?)",ordinal,batch.getBatchId());
    }
}
