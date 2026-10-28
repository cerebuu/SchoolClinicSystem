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
    private static final Set<String> WRITES = Set.of("registerPatient", "updatePatient", "addAllergy", "recordVisit",
        "recordVisitWithCampus","recordVisitAndAdminister","recordVisitAndAdministerWithCampus",
        "dispenseMedicine", "addMedicine", "restockMedicine", "mergeMedicineBatch", "mergeDuplicateMedicineBatches", "refillBatch",
        "saveInventoryBatch","refillInventoryBatch",
        "dispenseMedicineWithCampus",
        "createGuardianNotification", "createGuardianNotificationWithCampus",
        "createEmergencyNotification", "createEmergencyNotificationWithCampus",
        "updateNotificationStatus", "updateNotificationStatusForCampus",
        "beginNotificationAttempt", "beginNotificationAttemptForCampus",
        "completeNotificationAttempt", "completeNotificationAttemptForCampus");
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
                if(method.getName().equals("transactionPage")&&args.length==5)return DispenseRepository.transactions(connection,(int)args[0],(int)args[1],(String)args[2],(LocalDateTime)args[3],(LocalDateTime)args[4]);
                if(method.getName().equals("transactionPage")&&args.length==6)return DispenseRepository.transactions(connection,
                    (int)args[0],(int)args[1],(String)args[2],(LocalDateTime)args[3],(LocalDateTime)args[4],(String)args[5]);
                if(method.getName().equals("medicationHistoryPage"))return medicationHistory(connection,(int)args[0],(int)args[1],
                    (String)args[2],(LocalDateTime)args[3],(LocalDateTime)args[4],(String)args[5]);
                if(method.getName().equals("recordPage")&&args.length==4)return recordPage(connection,
                    (int)args[0],(int)args[1],(String)args[2],(String)args[3]);
                if(method.getName().equals("recordCount")&&args!=null&&args.length==1) {
                    String campusId=(String)args[0];
                    return campusId==null?count(connection,"SELECT COUNT(*) FROM visits"):
                        count(connection,"SELECT COUNT(*) FROM visits WHERE campus_id=?",campusId);
                }
                if(method.getName().equals("searchRecords")&&args.length==7)
                    return searchRecords(connection,(String)args[0],(String)args[1],(String)args[2],
                        (LocalDate)args[3],(String)args[4],(String)args[5],(String)args[6]);
                if(method.getName().equals("recentRecordPage")&&args.length==5)return recentRecordPage(connection,
                    (int)args[0],(int)args[1],(String)args[2],(String)args[3],null,null,(String)args[4],false);
                if(method.getName().equals("recentRecordPage")&&args.length==7)return recentRecordPage(connection,
                    (int)args[0],(int)args[1],(String)args[2],(String)args[3],(LocalDateTime)args[4],
                    (LocalDateTime)args[5],(String)args[6],true);
                if(method.getName().equals("matchingMedicineBatches")&&args[0] instanceof MedicineProduct product)return Sql.query(connection,
                    "SELECT b.*,i.batch_id FROM medicine_batches b JOIN medicine_batch_ids i USING(ordinal) WHERE LOWER(TRIM(b.name))=LOWER(TRIM(?)) AND LOWER(TRIM(b.strength))=LOWER(TRIM(?)) AND LOWER(TRIM(b.formulation))=LOWER(TRIM(?)) AND b.expiry=? AND b.expiry>=? ORDER BY b.batch_date,b.ordinal",
                    r->medicine(r),product.name(),product.strength(),product.formulation(),args[1],LocalDate.now());
                if(method.getName().equals("matchingMedicineBatches"))return Sql.query(connection,
                    "SELECT b.*,i.batch_id FROM medicine_batches b JOIN medicine_batch_ids i USING(ordinal) WHERE LOWER(TRIM(b.name))=LOWER(TRIM(?)) AND b.expiry=? AND b.expiry>=? ORDER BY b.batch_date,b.ordinal",
                    ClinicRepository::medicine,args[0],args[1],LocalDate.now());
                // Frequent dashboard lookups must not materialize unrelated clinical history.
                if(method.getName().equals("patientCount")) return count(connection,"SELECT COUNT(*) FROM patients");
                if(method.getName().equals("recordCount")) return count(connection,"SELECT COUNT(*) FROM visits");
                if(method.getName().equals("findPatient")) return Sql.query(connection,"SELECT * FROM patients WHERE id=?",
                    row -> patient(connection,row),args[0]).stream().findFirst();
                if(method.getName().equals("getVisitHistory")&&args.length==2) {
                    String campusId=(String)args[1];
                    if(Sql.query(connection,"SELECT COUNT(*) FROM patients WHERE id=?",r->r.getInt(1),args[0]).get(0)==0)
                        throw new IllegalArgumentException("Patient not found.");
                    String sql="SELECT * FROM visits WHERE patient_id=?"+(campusId==null?"":" AND campus_id=?")+" ORDER BY seq";
                    return campusId==null?Sql.query(connection,sql,r->visit(connection,r),args[0])
                        :Sql.query(connection,sql,r->visit(connection,r),args[0],campusId);
                }
                if(method.getName().equals("getStock")&&args[0] instanceof MedicineProduct product)return Sql.query(connection,
                    "SELECT COALESCE(SUM(CAST(quantity AS BIGINT)),0) FROM medicine_batches WHERE LOWER(TRIM(name))=LOWER(TRIM(?)) AND LOWER(TRIM(strength))=LOWER(TRIM(?)) AND LOWER(TRIM(formulation))=LOWER(TRIM(?)) AND expiry>=?",
                    row->Math.toIntExact(row.getLong(1)),product.name(),product.strength(),product.formulation(),LocalDate.now()).get(0);
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

    private static int count(Connection c,String sql,Object... args) throws SQLException {
        return Sql.query(c,sql,r -> Math.toIntExact(r.getLong(1)),args).get(0);
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

    private static Medicine medicine(ResultSet r) throws SQLException {
        return new Medicine(r.getString("batch_id"),
            new MedicineProduct(r.getString("name"),r.getString("strength"),r.getString("formulation")),
            r.getInt("quantity"),r.getObject("expiry",LocalDate.class),
            r.getObject("batch_date",LocalDate.class),r.getString("remarks"));
    }

    private static InMemoryClinicSystem.State load(Connection c) throws SQLException {
        List<Patient> patients = Sql.query(c,"SELECT * FROM patients ORDER BY id", r -> patient(c,r));
        List<MedicalRecord> visits=Sql.query(c,"SELECT * FROM visits ORDER BY seq",r -> new MedicalRecord(
            r.getString("id"),r.getString("patient_id"),
            Sql.query(c,"SELECT symptom FROM symptoms WHERE visit_id=? ORDER BY ordinal",x -> x.getString(1),r.getString("id")),
            r.getString("reason"),r.getString("assessment"),r.getString("medication"),r.getString("staff_id"),
            r.getObject("visit_time",LocalDateTime.class),r.getString("campus_id")));
        List<Medicine> batches=Sql.query(c,"SELECT b.*,i.batch_id FROM medicine_batches b JOIN medicine_batch_ids i USING(ordinal) ORDER BY b.ordinal",ClinicRepository::medicine);
        List<InventoryMovement> movements=Sql.query(c,"SELECT * FROM inventory_movements ORDER BY seq",r->new InventoryMovement(
            r.getString("id"),InventoryMovement.Type.valueOf(r.getString("event_type")),
            new MedicineProduct(r.getString("medicine"),r.getString("strength"),r.getString("formulation")),
            r.getInt("quantity"),r.getObject("expiry",LocalDate.class),r.getString("batch_id"),
            r.getObject("event_time",LocalDateTime.class),r.getString("actor_id"),r.getString("campus_id")));
        List<MedicineTransaction> transactions=Sql.query(c,"SELECT * FROM dispensing ORDER BY seq",r -> {
            List<BatchAllocation> allocations=Sql.query(c,
                "SELECT batch_id,quantity,expiry FROM dispensing_batch_allocations WHERE transaction_id=? ORDER BY expiry,batch_id",
                a->new BatchAllocation(a.getString("batch_id"),a.getInt("quantity"),a.getObject("expiry",LocalDate.class)),
                r.getString("id"));
            return new MedicineTransaction(r.getString("id"),
                new MedicineProduct(r.getString("medicine"),r.getString("strength"),r.getString("formulation")),
                r.getString("patient_id"),r.getString("staff_id"),r.getInt("quantity"),
                r.getObject("expiry",LocalDate.class),r.getString("remarks"),
                r.getObject("dispensed_at",LocalDateTime.class),r.getString("visit_id"),allocations,
                r.getString("campus_id"));
        });
        List<NotificationLog> notifications=Sql.query(c,"""
            SELECT n.*, p.name AS patient_name, d.medicine FROM notifications n
            JOIN patients p ON p.id=n.patient_id LEFT JOIN dispensing d ON d.id=n.transaction_id ORDER BY n.seq
            """,r -> {
                NotificationLog n=new NotificationLog(r.getString("id"),r.getString("patient_id"),r.getString("transaction_id"),
                    r.getString("reason"),r.getObject("created_at",LocalDateTime.class),r.getString("patient_name"),r.getString("medicine"),
                    GuardianMessageTemplates.MessageType.valueOf(r.getString("message_type")),"the school clinic",
                    r.getString("recipient_label"),r.getString("recipient_contact"),r.getString("campus_id"));
                String channel=r.getString("channel");
                n.restoreState(NotificationStatus.valueOf(r.getString("status")),
                    channel==null?null:clinic.notify.Channel.valueOf(channel),r.getInt("attempt_count"),
                    r.getObject("last_attempt_at",LocalDateTime.class));
                return n;
            });
        return new InMemoryClinicSystem.State(patients,visits,transactions,notifications,batches,movements);
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
            } else if(!sameProfile(old,p)) {
                Object departmentOrCourse=p instanceof Student student?student.getCourse():((Employee)p).getDepartment();
                Object sectionOrPosition=p instanceof Student student?student.getSection():((Employee)p).getPosition();
                Object yearLevel=p instanceof Student student?student.getYearLevel():null;
                int changed=Sql.update(c,"UPDATE patients SET name=?,contact=?,patient_type=?,course_department=?,section_position=?,year_level=?,guardian_contact=? WHERE id=?",
                    p.getName(),p.getContactInfo(),p.getPatientType(),departmentOrCourse,sectionOrPosition,yearLevel,p.getGuardianContact(),p.getId());
                if(changed!=1)throw new IllegalStateException("Patient profile could not be updated.");
            }
            for(String allergy:p.getAllergies()) if(old==null||!old.hasAllergy(allergy))
                Sql.update(c,"INSERT INTO allergies VALUES(?,?)",p.getId(),allergy);
        }
        Set<String> visits=new HashSet<>();
        before.records().forEach(r -> visits.add(r.getRecordId()));
        for(MedicalRecord r:after.records()) if(!visits.contains(r.getRecordId())) {
            Sql.update(c,"INSERT INTO visits(id,patient_id,reason,assessment,medication,staff_id,visit_time,campus_id) VALUES(?,?,?,?,?,?,?,?)",
                r.getRecordId(),r.getPatientId(),r.getReason(),r.getAssessment(),r.getMedicationGiven(),
                r.getStaffId(),r.getVisitDateTime(),r.getCampusId());
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
                    ||!batch.getBatchDate().equals(before.batches().get(i).getBatchDate())
                    ||!batch.getStrength().equals(before.batches().get(i).getStrength())
                    ||!batch.getFormulation().equals(before.batches().get(i).getFormulation()))
                Sql.update(c,"UPDATE medicine_batches SET quantity=?,expiry=?,batch_date=?,remarks=?,strength=?,formulation=? WHERE ordinal=?",
                    batch.getQuantity(),batch.getExpirationDate(),batch.getBatchDate(),batch.getRemarks(),
                    batch.getStrength(),batch.getFormulation(),i);
        }
        Set<String> transactions=new HashSet<>();
        before.transactions().forEach(t -> transactions.add(t.getTransactionId()));
        for(MedicineTransaction t:after.transactions()) if(!transactions.contains(t.getTransactionId())) {
            Sql.update(c,"INSERT INTO dispensing(id,medicine,patient_id,staff_id,quantity,expiry,remarks,dispensed_at,strength,formulation,visit_id,campus_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                t.getTransactionId(),t.getMedicineName(),t.getPatientId(),t.getStaffId(),t.getQuantity(),t.getExpirationDate(),
                t.getRemarks(),t.getDateTime(),t.getMedicineProduct().strength(),t.getMedicineProduct().formulation(),
                t.getVisitId(),t.getCampusId());
            for(BatchAllocation allocation:t.getBatchAllocations())
                Sql.update(c,"INSERT INTO dispensing_batch_allocations(transaction_id,batch_id,quantity,expiry) VALUES(?,?,?,?)",
                    t.getTransactionId(),allocation.batchId(),allocation.quantity(),allocation.expiry());
        }
        Set<String> movements=new HashSet<>();
        before.inventoryMovements().forEach(m->movements.add(m.id()));
        for(InventoryMovement movement:after.inventoryMovements())if(!movements.contains(movement.id()))
            Sql.update(c,"INSERT INTO inventory_movements(id,event_type,medicine,strength,formulation,quantity,expiry,batch_id,event_time,actor_id,campus_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                movement.id(),movement.type().name(),movement.medicine().name(),movement.medicine().strength(),
                movement.medicine().formulation(),movement.quantity(),movement.expiry(),movement.batchId(),
                movement.dateTime(),movement.actorId(),movement.campusId());
        Map<String,NotificationLog> previousNotifications=new HashMap<>();
        before.notifications().forEach(n -> previousNotifications.put(n.getNotificationId(),n));
        for(NotificationLog n:after.notifications()) {
            NotificationLog old=previousNotifications.get(n.getNotificationId());
            if(old==null)
                Sql.update(c,"INSERT INTO notifications(id,patient_id,transaction_id,reason,created_at,status,recipient_label,recipient_contact,message_type,channel,attempt_count,last_attempt_at,campus_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    n.getNotificationId(),n.getPatientId(),n.getTransactionId(),n.getReason(),n.getDateTime(),n.getStatus().name(),
                    n.getRecipientLabel(),n.getRecipientContact(),n.getMessageType().name(),
                    n.getChannel()==null?null:n.getChannel().name(),n.getAttemptCount(),n.getLastAttemptTime(),n.getCampusId());
            else if(old.getStatus()!=n.getStatus()||old.getChannel()!=n.getChannel()
                    ||old.getAttemptCount()!=n.getAttemptCount()
                    ||!Objects.equals(old.getLastAttemptTime(),n.getLastAttemptTime()))
                Sql.update(c,"UPDATE notifications SET status=?,channel=?,attempt_count=?,last_attempt_at=? WHERE id=?",
                    n.getStatus().name(),n.getChannel()==null?null:n.getChannel().name(),n.getAttemptCount(),
                    n.getLastAttemptTime(),n.getNotificationId());
        }
    }
    private static List<MedicalRecord> searchRecords(Connection c,String patient,String symptom,String medicine,
            LocalDate date,String section,String department,String campusId) throws SQLException {
        StringBuilder where=new StringBuilder(" FROM visits v JOIN patients p ON p.id=v.patient_id WHERE 1=1");
        List<Object> args=new ArrayList<>();
        if(campusId!=null){where.append(" AND v.campus_id=?");args.add(campusId);}
        if(patient!=null&&!patient.isBlank()) {
            where.append(" AND LOCATE(?,LOWER(v.patient_id))>0");args.add(patient.trim().toLowerCase(Locale.ROOT));
        }
        if(symptom!=null&&!symptom.isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM symptoms s WHERE s.visit_id=v.id AND LOCATE(?,LOWER(s.symptom))>0)");
            args.add(symptom.trim().toLowerCase(Locale.ROOT));
        }
        if(date!=null){where.append(" AND CAST(v.visit_time AS DATE)=?");args.add(date);}
        if(section!=null&&!section.isBlank()) {
            where.append(" AND p.patient_type='STUDENT' AND LOCATE(?,LOWER(p.section_position))>0");
            args.add(section.trim().toLowerCase(Locale.ROOT));
        }
        if(department!=null&&!department.isBlank()) {
            where.append(" AND p.patient_type='EMPLOYEE' AND LOCATE(?,LOWER(p.course_department))>0");
            args.add(department.trim().toLowerCase(Locale.ROOT));
        }
        if(medicine!=null&&!medicine.isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM dispensing d WHERE d.patient_id=v.patient_id"
                +" AND d.visit_id=v.id");
            if(campusId!=null){where.append(" AND d.campus_id=?");args.add(campusId);}
            where.append(" AND LOCATE(?,LOWER(d.medicine))>0)");
            args.add(medicine.trim().toLowerCase(Locale.ROOT));
        }
        return Sql.query(c,"SELECT v.*"+where+" ORDER BY v.seq",r->visit(c,r),args.toArray());
    }

    private static PageResult<MedicalRecord> recordPage(Connection c,int page,int requestedSize,String query,
            String campusId) throws SQLException {
        int size=DispenseQueries.size(requestedSize);
        if(page<0)throw new IllegalArgumentException("Invalid page.");
        String needle=DispenseQueries.query(query);
        String where=" FROM visits WHERE (LOCATE(?,LOWER(patient_id))>0 OR LOCATE(?,LOWER(reason))>0)";
        List<Object> args=new ArrayList<>(List.of(needle,needle));
        if(campusId!=null){where+=" AND campus_id=?";args.add(campusId);}
        int total=Sql.query(c,"SELECT COUNT(*)"+where,r->r.getInt(1),args.toArray()).get(0);
        if((long)page*size>=total)return new PageResult<>(List.of(),total,page,size);
        args.add(size);args.add((long)page*size);
        List<MedicalRecord> rows=Sql.query(c,"SELECT *"+where+" ORDER BY seq LIMIT ? OFFSET ?",
            r->visit(c,r),args.toArray());
        return new PageResult<>(rows,total,page,size);
    }

    private static PageResult<MedicalRecord> recentRecordPage(Connection c,int page,int requestedSize,
            String query,String staffId,LocalDateTime from,LocalDateTime to,String campusId,boolean dateRange)
            throws SQLException {
        int size=DispenseQueries.size(requestedSize);
        if(page<0)throw new IllegalArgumentException("Invalid page.");
        if(dateRange&&(to==null||from!=null&&from.isAfter(to)))
            throw new IllegalArgumentException("A valid visit date range is required.");
        String needle=DispenseQueries.query(query);
        String fromSql=dateRange?" FROM visits v JOIN patients p ON p.id=v.patient_id":" FROM visits v";
        String where=fromSql+" WHERE 1=1";
        List<Object> args=new ArrayList<>();
        if(dateRange) {
            if(!needle.isEmpty()) {
                where+=" AND (LOCATE(?,LOWER(v.patient_id))>0 OR LOCATE(?,LOWER(p.name))>0)";
                args.add(needle);args.add(needle);
            }
        } else {
            where+=" AND (LOCATE(?,LOWER(v.patient_id))>0 OR LOCATE(?,LOWER(v.reason))>0)";
            args.add(needle);args.add(needle);
        }
        if(staffId!=null){where+=" AND v.staff_id=?";args.add(staffId);}
        if(campusId!=null){where+=" AND v.campus_id=?";args.add(campusId);}
        if(dateRange) {
            if(from!=null){where+=" AND v.visit_time>=?";args.add(from);}
            where+=" AND v.visit_time<=?";args.add(to);
        }
        int total=Sql.query(c,"SELECT COUNT(*)"+where,r->r.getInt(1),args.toArray()).get(0);
        if((long)page*size>=total)return new PageResult<>(List.of(),total,page,size);
        args.add(size);args.add((long)page*size);
        List<MedicalRecord> rows=Sql.query(c,"SELECT v.*"+where+" ORDER BY v.visit_time DESC,v.seq DESC LIMIT ? OFFSET ?",
            r->visit(c,r),args.toArray());
        return new PageResult<>(rows,total,page,size);
    }

    private static MedicalRecord visit(Connection c,ResultSet r) throws SQLException {
        String id=r.getString("id");
        return new MedicalRecord(id,r.getString("patient_id"),
            Sql.query(c,"SELECT symptom FROM symptoms WHERE visit_id=? ORDER BY ordinal",x->x.getString(1),id),
            r.getString("reason"),r.getString("assessment"),r.getString("medication"),r.getString("staff_id"),
            r.getObject("visit_time",LocalDateTime.class),r.getString("campus_id"));
    }

    private static PageResult<MedicationHistoryEntry> medicationHistory(Connection c,int page,int requestedSize,
            String query,LocalDateTime from,LocalDateTime to,String campusId) throws SQLException {
        int size=DispenseQueries.size(requestedSize);
        if(page<0)throw new IllegalArgumentException("Invalid page.");
        String normalized=DispenseQueries.query(query);
        String base=" FROM (SELECT id,dispensed_at AS event_time,'DISPENSE' AS event_type,medicine,strength,formulation,quantity,staff_id AS actor_id,campus_id,patient_id,visit_id,expiry,CAST(NULL AS VARCHAR(500)) AS batch_id,CAST(remarks AS VARCHAR) AS remarks FROM dispensing"
            +" UNION ALL SELECT id,event_time,event_type,medicine,strength,formulation,quantity,actor_id,campus_id,CAST(NULL AS VARCHAR(500)) AS patient_id,CAST(NULL AS VARCHAR(500)) AS visit_id,expiry,batch_id,'' AS remarks FROM inventory_movements) h WHERE 1=1";
        String where=base;
        List<Object> args=new ArrayList<>();
        if(!normalized.isEmpty()) {
            where+=" AND LOCATE(?,LOWER(id || ' ' || event_type || ' ' || medicine || ' ' || strength || ' ' || formulation || ' ' || actor_id || ' ' || COALESCE(campus_id,'') || ' ' || COALESCE(patient_id,'') || ' ' || COALESCE(batch_id,'')))>0";
            args.add(normalized);
        }
        if(from!=null){where+=" AND event_time>=?";args.add(from);}
        if(to!=null){where+=" AND event_time<?";args.add(to);}
        if(campusId!=null){where+=" AND campus_id=?";args.add(campusId);}
        int total=Sql.query(c,"SELECT COUNT(*)"+where,r->r.getInt(1),args.toArray()).get(0);
        if((long)page*size>=total)return new PageResult<>(List.of(),total,page,size);
        args.add(size);args.add((long)page*size);
        List<MedicationHistoryEntry> rows=Sql.query(c,"SELECT *"+where+" ORDER BY event_time DESC,id DESC LIMIT ? OFFSET ?",r->{
            MedicationHistoryEntry.Type type=MedicationHistoryEntry.Type.valueOf(r.getString("event_type"));
            List<BatchAllocation> allocations=type==MedicationHistoryEntry.Type.DISPENSE
                ?Sql.query(c,"SELECT batch_id,quantity,expiry FROM dispensing_batch_allocations WHERE transaction_id=? ORDER BY expiry,batch_id",
                    a->new BatchAllocation(a.getString("batch_id"),a.getInt("quantity"),a.getObject("expiry",LocalDate.class)),r.getString("id"))
                :List.of();
            return new MedicationHistoryEntry(r.getString("id"),type,r.getObject("event_time",LocalDateTime.class),
                new MedicineProduct(r.getString("medicine"),r.getString("strength"),r.getString("formulation")),
                r.getInt("quantity"),r.getString("actor_id"),r.getString("campus_id"),r.getString("patient_id"),
                r.getString("visit_id"),r.getObject("expiry",LocalDate.class),r.getString("batch_id"),
                allocations,r.getString("remarks"));
        },args.toArray());
        return new PageResult<>(rows,total,page,size);
    }

    private static boolean sameProfile(Patient first,Patient second) {
        if(!first.getName().equals(second.getName())
                ||!first.getContactInfo().equals(second.getContactInfo())
                ||!Objects.equals(first.getGuardianContact(),second.getGuardianContact())
                ||!first.getPatientType().equals(second.getPatientType())
                ||first.getClass()!=second.getClass())return false;
        if(first instanceof Student current&&second instanceof Student updated)
            return current.getCourse().equals(updated.getCourse())
                &&current.getSection().equals(updated.getSection())
                &&current.getYearLevel()==updated.getYearLevel();
        if(first instanceof Employee current&&second instanceof Employee updated)
            return current.getDepartment().equals(updated.getDepartment())
                &&current.getPosition().equals(updated.getPosition());
        return true;
    }
    private static void insertBatch(Connection c,int ordinal,Medicine batch) throws SQLException {
        Sql.update(c,"INSERT INTO medicine_batches(ordinal,name,quantity,expiry,batch_date,remarks,strength,formulation) VALUES(?,?,?,?,?,?,?,?)",
            ordinal,batch.getName(),batch.getQuantity(),batch.getExpirationDate(),batch.getBatchDate(),batch.getRemarks(),
            batch.getStrength(),batch.getFormulation());
        Sql.update(c,"INSERT INTO medicine_batch_ids(ordinal,batch_id) VALUES(?,?)",ordinal,batch.getBatchId());
    }
}
