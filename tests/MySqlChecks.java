package clinic.persistence;

import clinic.service.ClinicData;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Explicit opt-in: upgrades the configured existing schema; clinical queries are read-only. */
public final class MySqlChecks {
    public static void main(String[] args) throws Exception {
        if(!Arrays.equals(args,new String[]{"--existing"})||System.getenv("SCMS_DB_URL")==null)
            throw new IllegalArgumentException("Set the three SCMS_DB variables and pass --existing. This runs additive migrations.");
        String url=System.getenv("SCMS_DB_URL").replaceFirst("^jdbc:mariadb:","jdbc:mysql:");
        Map<String,String> before;
        try(Connection c=DriverManager.getConnection(url,System.getenv("SCMS_DB_USER"),System.getenv("SCMS_DB_PASSWORD"))) {
            before=fingerprint(c);
            System.out.println("Server: "+c.getMetaData().getDatabaseProductName()+" "+c.getMetaData().getDatabaseProductVersion());
        }
        for(int pass=0;pass<2;pass++)try(Database db=Database.fromEnvironment(Path.of("unused-mysql-fallback"))) {
            if(!db.isRemote())throw new AssertionError("MySQL configuration required");
            ClinicData clinic=ClinicRepository.create(db);
            int patients=clinic.patientPage(0,25,"").total();
            int batches=clinic.medicinePage(0,25,"").total();
            int visits=clinic.recentRecordPage(0,25,"",null,null).total();
            if(patients!=70||batches!=7||visits!=4)throw new AssertionError("Expected existing counts 70/7/4");
            clinic.dispensableMedicinePage(0,2,"para");
            clinic.dispensableMedicinePage(1,2,"");
            if(clinic.medicinePage(0,25,"Paracetamol 500mg").rows().stream()
                    .noneMatch(m->m.getName().equals("Paracetamol 500mg")))throw new AssertionError("Legacy name changed");
            clinic.getStock("Paracetamol 500mg");
            clinic.transactionPage(0,2,"para",null,null);
            clinic.medicationHistoryPage(0,2,"para",null,null,null);
            clinic.medicationHistoryPage(0,2,"",null,null,null);
            clinic.recordPage(0,2,"",null);
            clinic.searchRecords("","","",null,"","",null);
            clinic.notificationPage(0,25,"");
            var admin=new AdminRepository(db);
            admin.auditPage(0,5,new String[]{"date:2026"});
            admin.auditPage(1,5,new String[]{"null"});
            admin.auditSummary(java.time.LocalDate.now());
            var accounts=new AccountRepository(db).load();
            if(accounts.stream().noneMatch(a->a.user().getUserId().equals("N001")))throw new AssertionError("N001 missing");
            db.transaction(c->{
                if(!before.equals(fingerprint(c)))throw new AssertionError("Existing base data changed");
                System.out.println("PASS: schema "+Sql.query(c,"SELECT version FROM schema_version WHERE id=1",r->r.getInt(1)).get(0)
                    +", patients="+patients+", batches="+batches+", recent visits="+visits+" (unscoped repository).");
                return null;
            });
            if(pass==0) {
                JdbcValueChecks.run(db);
                schemaGuards(db);
            }
            System.out.println("PASS: repository searches/paging, legacy medicine names, account loading; existing base rows/hashes unchanged.");
            if(accounts.stream().anyMatch(a->a.user().getUserId().equals("N001")&&a.user().getCampusId()==null))
                System.out.println("MANUAL CHECK BLOCKED: N001 has no campus; legacy visits have no campus. Administrator assignment and approved visit backfill are required for nurse-scoped UI access.");
        }
        try(var app=clinic.app.ApplicationContext.production()) {
            if(!app.isPersistent()||!app.storageDescription().contains("MySQL"))throw new AssertionError("Production connection");
        }
        try(Connection c=DriverManager.getConnection(url,System.getenv("SCMS_DB_USER"),System.getenv("SCMS_DB_PASSWORD"))) {
            if(!before.equals(fingerprint(c)))throw new AssertionError("Production startup seeded or changed base rows");
        }
        System.out.println("PASS: production application startup selects MySQL without seeding existing tables.");
    }
    /** Temporary tables shadow names for one session; persistent application tables are untouched. */
    private static void schemaGuards(Database db) {
        db.transaction(c->{
            MySqlMetadata.add(c,"accounts","id","INT"); // Must skip an existing column, regardless of type.
            MySqlMetadata.index(c,"visits","visit_patient_time","patient_id,visit_time");
            MySqlMetadata.execute(c,"CREATE TEMPORARY TABLE schema_version(id INT,version INT)");
            try {
                Sql.update(c,"INSERT INTO schema_version VALUES(1,99)");
                try { Schema.migrate(c);throw new AssertionError("Unsupported version accepted"); }
                catch(SchemaException expected) { /* No DDL is run against the real schema. */ }
            } finally { MySqlMetadata.execute(c,"DROP TEMPORARY TABLE schema_version"); }
            MySqlMetadata.execute(c,"CREATE TEMPORARY TABLE visits(id VARCHAR(100))");
            try {
                try { Schema.migrate(c);throw new AssertionError("Missing columns accepted"); }
                catch(SchemaException expected) {
                    if(!expected.getMessage().contains("visits"))throw new AssertionError("Unclear schema diagnostic");
                }
            } finally { MySqlMetadata.execute(c,"DROP TEMPORARY TABLE visits"); }
            System.out.println("PASS: unsupported-version/incomplete-schema guards and retryable column/index metadata checks.");
            return null;
        });
    }
    /** Fingerprints original columns only. No names, contacts, passwords or hash values are printed. */
    private static Map<String,String> fingerprint(Connection c) throws Exception {
        Map<String,String> fields=Map.ofEntries(
            Map.entry("accounts","id,name,role,password_hash,active,failed_attempts,locked_until"),
            Map.entry("campuses","id,name"),Map.entry("audit_entries","seq,event_time,user_id,user_name,role,action,details,allowed"),
            Map.entry("patients","id,name,contact,patient_type,course_department,section_position,year_level,guardian_contact"),
            Map.entry("allergies","patient_id,allergy"),Map.entry("patient_history","patient_id,ordinal,detail"),
            Map.entry("visits","seq,id,patient_id,reason,assessment,medication,staff_id,visit_time"),
            Map.entry("symptoms","visit_id,ordinal,symptom"),Map.entry("medicine_batches","ordinal,name,quantity,expiry,batch_date,remarks"),
            Map.entry("medicine_batch_ids","ordinal,batch_id"),
            Map.entry("dispensing","seq,id,medicine,patient_id,staff_id,quantity,expiry,remarks,dispensed_at"),
            Map.entry("notifications","seq,id,patient_id,transaction_id,reason,created_at,status,recipient_label,recipient_contact"));
        Map<String,String> result=new TreeMap<>();
        for(var field:fields.entrySet()) {
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT "+field.getValue()+" FROM "+field.getKey()+" ORDER BY "+field.getValue())) {
                while(r.next())for(int i=1;i<=r.getMetaData().getColumnCount();i++) {
                    String value=r.getString(i);
                    byte[] bytes=(value==null?"<null>":value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);
                }
            }
            result.put(field.getKey(),HexFormat.of().formatHex(digest.digest()));
        }
        return result;
    }
}
