import clinic.model.*;
import clinic.persistence.ClinicRepository;
import clinic.persistence.Database;
import clinic.service.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Verifies latest-first paging, filters, and insertion-order ties for both data sources. */
public final class TimeOrderingChecks {
    private static final LocalDateTime BASE = LocalDateTime.of(2025,1,1,0,0);
    private static final String PATIENT_ID = "patient-match";
    private static final String STAFF_ID = "ordering-nurse";
    private static final String CAMPUS_ID = "uic-main";

    public static void main(String[] args) throws Exception {
        List<MedicalRecord> records=records();
        InMemoryClinicSystem memory=new InMemoryClinicSystem(new InMemoryClinicSystem.State(
            List.of(patient()),records,List.of(),List.of(),List.of()));
        verifyVisitPages("in-memory",memory);

        Path directory=Files.createTempDirectory("scms-time-order-");
        try(Database database=new Database(directory.resolve("clinic"))) {
            ClinicData persistent=ClinicRepository.create(database);
            persistent.registerPatient(patient());
            database.transaction(connection->{
                try(var statement=connection.prepareStatement(
                        "INSERT INTO accounts(id,name,role,password_hash,active) VALUES(?,?,?,?,TRUE)")) {
                    statement.setString(1,STAFF_ID);
                    statement.setString(2,"Ordering Nurse");
                    statement.setString(3,"NURSE");
                    statement.setString(4,"test-hash");
                    statement.executeUpdate();
                }
                try(var visit=connection.prepareStatement(
                        "INSERT INTO visits(id,patient_id,reason,assessment,medication,staff_id,visit_time,campus_id) "
                            +"VALUES(?,?,?,?,?,?,?,?)");
                    var symptom=connection.prepareStatement(
                        "INSERT INTO symptoms(visit_id,ordinal,symptom) VALUES(?,0,'Cough')")) {
                    for(int i=0;i<records.size();i++) {
                        MedicalRecord record=records.get(i);
                        visit.setString(1,record.getRecordId());
                        visit.setString(2,record.getPatientId());
                        visit.setString(3,record.getReason());
                        visit.setString(4,record.getAssessment());
                        visit.setString(5,record.getMedicationGiven());
                        visit.setString(6,record.getStaffId());
                        visit.setObject(7,record.getVisitDateTime());
                        visit.setString(8,record.getCampusId());
                        visit.executeUpdate();
                        symptom.setString(1,record.getRecordId());
                        symptom.executeUpdate();
                    }
                }
                return null;
            });
            verifyVisitPages("database",persistent);
        } finally {
            try(var paths=Files.walk(directory)) {
                for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
            }
        }
        verifyOtherTimeLists();
        System.out.println("PASS: time ordering checks.");
    }

    private static void verifyVisitPages(String label,ClinicData data) {
        List<Integer> expected=orderedIndexes();
        check(label+" first page",ids(expected.subList(0,25)),
            recordIds(data.recordPage(0,25,"",CAMPUS_ID)));
        check(label+" last page",ids(expected.subList(50,60)),
            recordIds(data.recordPage(2,25,"",CAMPUS_ID)));
        List<String> middlePage=recordIds(data.recordPage(1,25,"",CAMPUS_ID));
        check(label+" same-time tie uses latest insertion first",true,
            middlePage.indexOf("visit-21")<middlePage.indexOf("visit-20"));

        List<Integer> filtered=expected.stream().filter(i->i%3==0).toList();
        check(label+" search is filtered before paging",ids(filtered),
            recordIds(data.recordPage(0,25,"keep",CAMPUS_ID)));
        LocalDateTime from=BASE.plusMinutes(10),to=BASE.plusMinutes(30);
        List<Integer> dateFiltered=expected.stream()
            .filter(i->!recordTime(i).isBefore(from)&&!recordTime(i).isAfter(to)).toList();
        check(label+" date filter and patient search retain order",ids(dateFiltered),
            recordIds(data.recentRecordPage(0,25,"Patient Match",STAFF_ID,from,to,CAMPUS_ID)));

        MedicalRecord inserted=data.recordVisitWithCampus(PATIENT_ID,List.of("Cough"),"New record","",
            STAFF_ID,CAMPUS_ID);
        check(label+" newly saved record is first",inserted.getRecordId(),
            data.recordPage(0,25,"",CAMPUS_ID).rows().get(0).getRecordId());
    }

    private static void verifyOtherTimeLists() {
        List<MedicineTransaction> transactions=new ArrayList<>();
        for(int i=0;i<3;i++)transactions.add(new MedicineTransaction("transaction-"+i,"Medicine",
            PATIENT_ID,STAFF_ID,1,LocalDate.of(2025,6,1),"",i==2?BASE.plusMinutes(2):BASE));
        check("transaction ties use insertion order descending",
            List.of("transaction-2","transaction-1","transaction-0"),
            DispenseQueries.transactions(transactions,0,25,"",null,null).rows().stream()
                .map(MedicineTransaction::getTransactionId).toList());

        AuditLog audit=new AuditLog();
        audit.record(BASE,"actor","Actor","NURSE","older","",true);
        audit.record(BASE.plusMinutes(2),"actor","Actor","NURSE","newest","",true);
        audit.record(BASE.plusMinutes(2),"actor","Actor","NURSE","latest tie","",true);
        check("audit orders by time and insertion sequence",
            List.of("latest tie","newest","older"),
            audit.page(0,25,"").rows().stream().map(AuditEntry::getAction).toList());

        NotificationStore notifications=new NotificationStore();
        List<NotificationLog> saved=List.of(notification("old",BASE),
            notification("newer",BASE.plusMinutes(2)),notification("latest",BASE.plusMinutes(2)));
        notifications.restore(saved);
        check("notification log orders by time and insertion sequence",
            List.of("latest","newer","old"),
            notifications.page(0,25,"").rows().stream().map(NotificationLog::getNotificationId).toList());
    }

    private static NotificationLog notification(String id,LocalDateTime time) {
        return new NotificationLog(id,PATIENT_ID,null,"Clinic visit",time,"Patient Match",null,
            clinic.service.GuardianMessageTemplates.MessageType.CLINIC_VISIT,"the school clinic");
    }

    private static Student patient() {
        return new Student(PATIENT_ID,"Patient Match","contact","CS",1,"A");
    }

    private static List<MedicalRecord> records() {
        List<MedicalRecord> records=new ArrayList<>();
        for(int i=0;i<60;i++)records.add(new MedicalRecord(id(i),PATIENT_ID,List.of("Cough"),
            i%3==0?"keep concern":"other concern","","",STAFF_ID,recordTime(i),CAMPUS_ID));
        return records;
    }

    private static LocalDateTime recordTime(int index) {
        return BASE.plusMinutes(index==21?20:index);
    }

    private static List<Integer> orderedIndexes() {
        return java.util.stream.IntStream.range(0,60).boxed().sorted((first,second)->{
            int byTime=recordTime(second).compareTo(recordTime(first));
            return byTime==0?Integer.compare(second,first):byTime;
        }).toList();
    }

    private static List<String> ids(List<Integer> indexes) {
        return indexes.stream().map(TimeOrderingChecks::id).toList();
    }

    private static String id(int index) {
        return "visit-"+String.format(java.util.Locale.ROOT,"%02d",index);
    }

    private static List<String> recordIds(PageResult<MedicalRecord> page) {
        return page.rows().stream().map(MedicalRecord::getRecordId).toList();
    }

    private static void check(String label,Object expected,Object actual) {
        if(!Objects.equals(expected,actual))
            throw new AssertionError(label+" | expected="+expected+" | actual="+actual);
        System.out.println("PASS: "+label);
    }
}
