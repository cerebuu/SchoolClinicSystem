package clinic.persistence;

import java.sql.*;
import java.util.List;

/** Version 1 is additive and retryable: never drops, resets, or seeds application data. */
final class Schema {
    private Schema() {}
    static void migrate(Connection connection) throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS schema_version (id INT PRIMARY KEY CHECK(id=1), version INT NOT NULL)");
            int version=0;
            try (ResultSet r = s.executeQuery("SELECT version FROM schema_version WHERE id=1")) {
                if (r.next()) {
                    version=r.getInt(1);
                    if (version < 1 || version > 11) throw new SQLException("Unsupported database schema version.");
                }
            }
            if(version==0) {
                for (String sql : DDL.split(";")) if (!sql.isBlank()) s.execute(sql);
                s.executeUpdate("INSERT INTO schema_version VALUES (1,1)");
                version=1;
            }
            if(version==1) {
                s.execute("ALTER TABLE accounts ADD COLUMN IF NOT EXISTS failed_attempts INT NOT NULL DEFAULT 0 CHECK(failed_attempts>=0)");
                s.execute("ALTER TABLE accounts ADD COLUMN IF NOT EXISTS locked_until BIGINT NOT NULL DEFAULT 0");
                s.executeUpdate("UPDATE schema_version SET version=2 WHERE id=1");
                version=2;
            }
            if(version==2) {
                s.execute("ALTER TABLE patients ADD COLUMN IF NOT EXISTS guardian_contact VARCHAR(500)");
                s.execute("ALTER TABLE notifications ADD COLUMN IF NOT EXISTS recipient_label VARCHAR(100)");
                s.execute("ALTER TABLE notifications ADD COLUMN IF NOT EXISTS recipient_contact VARCHAR(500)");
                s.executeUpdate("UPDATE schema_version SET version=3 WHERE id=1");
                version=3;
            }
            if(version==3) {
                s.execute("ALTER TABLE medicine_batches ADD COLUMN IF NOT EXISTS remarks CLOB DEFAULT '' NOT NULL");
                s.executeUpdate("UPDATE medicine_batches SET remarks='' WHERE remarks IS NULL");
                s.executeUpdate("UPDATE schema_version SET version=4 WHERE id=1");
                version=4;
            }
            if(version==4) {
                s.execute("CREATE TABLE IF NOT EXISTS medicine_batch_ids (ordinal INT PRIMARY KEY, batch_id VARCHAR(36) NOT NULL UNIQUE)");
                assignMissingBatchIds(connection);
                s.executeUpdate("UPDATE schema_version SET version=5 WHERE id=1");
                version=5;
            }
            if(version==5) {
                s.execute("ALTER TABLE notifications ALTER COLUMN transaction_id DROP NOT NULL");
                s.execute("ALTER TABLE notifications ADD COLUMN IF NOT EXISTS message_type VARCHAR(30) NOT NULL DEFAULT 'MEDICINE_GIVEN'");
                s.execute("ALTER TABLE notifications ADD COLUMN IF NOT EXISTS channel VARCHAR(10)");
                s.execute("ALTER TABLE notifications ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0");
                s.execute("ALTER TABLE notifications ADD COLUMN IF NOT EXISTS last_attempt_at TIMESTAMP(9)");
                dropNotificationCheck(connection,"status");
                s.execute("ALTER TABLE notifications ADD CONSTRAINT notification_status_check CHECK(status IN ('PENDING','SENT','FAILED'))");
                dropNotificationCheck(connection,"message_type");
                s.execute("ALTER TABLE notifications ADD CONSTRAINT notification_message_type_check CHECK(message_type IN ('CLINIC_VISIT','MEDICINE_GIVEN','SENT_HOME','FOLLOW_UP_NEEDED','EMERGENCY'))");
                s.executeUpdate("UPDATE schema_version SET version=6 WHERE id=1");
                version=6;
            }
            if(version==6) {
                s.execute("ALTER TABLE medicine_batches ADD COLUMN IF NOT EXISTS strength VARCHAR(100) NOT NULL DEFAULT ''");
                s.execute("ALTER TABLE medicine_batches ADD COLUMN IF NOT EXISTS formulation VARCHAR(100) NOT NULL DEFAULT ''");
                s.execute("ALTER TABLE dispensing ADD COLUMN IF NOT EXISTS strength VARCHAR(100) NOT NULL DEFAULT ''");
                s.execute("ALTER TABLE dispensing ADD COLUMN IF NOT EXISTS formulation VARCHAR(100) NOT NULL DEFAULT ''");
                s.execute("ALTER TABLE dispensing ADD COLUMN IF NOT EXISTS visit_id VARCHAR(500)");
                s.execute("""
                    CREATE TABLE IF NOT EXISTS dispensing_batch_allocations (
                      transaction_id VARCHAR(500) NOT NULL REFERENCES dispensing(id),
                      batch_id VARCHAR(36) NOT NULL,
                      quantity INT NOT NULL CHECK(quantity>0),
                      expiry DATE NOT NULL,
                      PRIMARY KEY(transaction_id,batch_id))
                    """);
                s.executeUpdate("UPDATE schema_version SET version=7 WHERE id=1");
                version=7;
            }
            if(version==7) {
                s.execute("ALTER TABLE accounts ADD COLUMN IF NOT EXISTS campus_id VARCHAR(500)");
                s.execute("ALTER TABLE audit_entries ADD COLUMN IF NOT EXISTS campus_id VARCHAR(500)");
                s.executeUpdate("UPDATE schema_version SET version=8 WHERE id=1");
                version=8;
            }
            if(version==8) {
                s.execute("ALTER TABLE visits ADD COLUMN IF NOT EXISTS campus_id VARCHAR(500)");
                s.execute("ALTER TABLE dispensing ADD COLUMN IF NOT EXISTS campus_id VARCHAR(500)");
                s.executeUpdate("UPDATE schema_version SET version=9 WHERE id=1");
                version=9;
            }
            if(version==9) {
                s.execute("""
                    CREATE TABLE IF NOT EXISTS inventory_movements (
                      seq BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                      id VARCHAR(500) NOT NULL UNIQUE,
                      event_type VARCHAR(30) NOT NULL CHECK(event_type IN ('INITIAL_STOCK','RESTOCK','REFILL')),
                      medicine VARCHAR(500) NOT NULL,
                      strength VARCHAR(100) NOT NULL DEFAULT '',
                      formulation VARCHAR(100) NOT NULL DEFAULT '',
                      quantity INT NOT NULL CHECK(quantity>0),
                      expiry DATE NOT NULL,
                      batch_id VARCHAR(36),
                      event_time TIMESTAMP(9) NOT NULL,
                      actor_id VARCHAR(500) NOT NULL REFERENCES accounts(id),
                      campus_id VARCHAR(500) NOT NULL)
                    """);
                s.execute("CREATE INDEX IF NOT EXISTS inventory_movement_campus_time ON inventory_movements(campus_id,event_time)");
                s.execute("CREATE INDEX IF NOT EXISTS inventory_movement_medicine_time ON inventory_movements(medicine,event_time)");
                s.executeUpdate("UPDATE schema_version SET version=10 WHERE id=1");
                version=10;
            }
            if(version==10) {
                s.execute("ALTER TABLE notifications ADD COLUMN IF NOT EXISTS campus_id VARCHAR(500)");
                s.execute("CREATE INDEX IF NOT EXISTS notification_campus_time ON notifications(campus_id,created_at)");
                s.executeUpdate("UPDATE schema_version SET version=11 WHERE id=1");
            }
        }
    }
    private static void dropNotificationCheck(Connection connection,String column) throws SQLException {
        List<String> constraints=new java.util.ArrayList<>();
        try(PreparedStatement query=connection.prepareStatement("""
            SELECT tc.constraint_name FROM information_schema.table_constraints tc
            JOIN information_schema.check_constraints cc
              ON tc.constraint_catalog=cc.constraint_catalog
             AND tc.constraint_schema=cc.constraint_schema
             AND tc.constraint_name=cc.constraint_name
            WHERE tc.table_name='NOTIFICATIONS' AND tc.constraint_type='CHECK'
              AND LOWER(cc.check_clause) LIKE ?
            """)) {
            query.setString(1,"%"+column.toLowerCase(java.util.Locale.ROOT)+"%");
            try(ResultSet rows=query.executeQuery()) {
            while(rows.next())constraints.add(rows.getString(1));
            }
        }
        try(Statement statement=connection.createStatement()) {
            for(String constraint:constraints)
                statement.execute("ALTER TABLE notifications DROP CONSTRAINT \""+
                    constraint.replace("\"","\"\"")+"\"");
        }
    }
    private static void assignMissingBatchIds(Connection connection) throws SQLException {
        try(PreparedStatement rows=connection.prepareStatement("SELECT ordinal FROM medicine_batches WHERE ordinal NOT IN (SELECT ordinal FROM medicine_batch_ids)");
            ResultSet result=rows.executeQuery();
            PreparedStatement insert=connection.prepareStatement("INSERT INTO medicine_batch_ids(ordinal,batch_id) VALUES(?,?)")) {
            while(result.next()) {
                insert.setInt(1,result.getInt(1));insert.setString(2,java.util.UUID.randomUUID().toString());insert.executeUpdate();
            }
        }
    }
    private static final String DDL = """
        CREATE TABLE IF NOT EXISTS accounts (
          id VARCHAR(500) PRIMARY KEY, name VARCHAR(500) NOT NULL,
          role VARCHAR(20) NOT NULL CHECK(role IN ('ADMIN','NURSE','ASSISTANT','DOCTOR')),
          password_hash VARCHAR(200) NOT NULL, active BOOLEAN NOT NULL, campus_id VARCHAR(500));
        CREATE TABLE IF NOT EXISTS campuses (id VARCHAR(500) PRIMARY KEY, name VARCHAR(500) NOT NULL);
        CREATE TABLE IF NOT EXISTS audit_entries (
          seq BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, event_time TIMESTAMP(9) NOT NULL,
          user_id VARCHAR(500) NOT NULL, user_name VARCHAR(500) NOT NULL, role VARCHAR(20) NOT NULL,
          action VARCHAR(100) NOT NULL, details VARCHAR(500) NOT NULL, allowed BOOLEAN NOT NULL,
          campus_id VARCHAR(500));
        CREATE TABLE IF NOT EXISTS patients (
          id VARCHAR(500) PRIMARY KEY, name VARCHAR(500) NOT NULL, contact VARCHAR(500) NOT NULL,
          patient_type VARCHAR(20) NOT NULL CHECK(patient_type IN ('STUDENT','EMPLOYEE')),
          course_department VARCHAR(500) NOT NULL, section_position VARCHAR(500) NOT NULL,
          year_level INT, CHECK((patient_type='STUDENT' AND year_level>0) OR (patient_type='EMPLOYEE' AND year_level IS NULL)));
        CREATE TABLE IF NOT EXISTS allergies (
          patient_id VARCHAR(500) REFERENCES patients(id), allergy VARCHAR(500) NOT NULL, PRIMARY KEY(patient_id,allergy));
        CREATE TABLE IF NOT EXISTS patient_history (
          patient_id VARCHAR(500) REFERENCES patients(id), ordinal INT NOT NULL, detail VARCHAR(500) NOT NULL, PRIMARY KEY(patient_id,ordinal));
        CREATE TABLE IF NOT EXISTS visits (
          seq BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE, id VARCHAR(500) PRIMARY KEY,
          patient_id VARCHAR(500) NOT NULL REFERENCES patients(id), reason VARCHAR(500) NOT NULL,
          assessment CLOB NOT NULL, medication CLOB NOT NULL, staff_id VARCHAR(500) NOT NULL REFERENCES accounts(id),
          visit_time TIMESTAMP(9) NOT NULL, campus_id VARCHAR(500));
        CREATE TABLE IF NOT EXISTS symptoms (
          visit_id VARCHAR(500) REFERENCES visits(id), ordinal INT NOT NULL, symptom VARCHAR(500) NOT NULL, PRIMARY KEY(visit_id,ordinal));
        CREATE TABLE IF NOT EXISTS medicine_batches (
          ordinal INT PRIMARY KEY, name VARCHAR(500) NOT NULL, quantity INT NOT NULL CHECK(quantity>=0),
          expiry DATE NOT NULL, batch_date DATE NOT NULL, remarks CLOB NOT NULL,
          strength VARCHAR(100) NOT NULL DEFAULT '', formulation VARCHAR(100) NOT NULL DEFAULT '');
        CREATE TABLE IF NOT EXISTS dispensing (
          seq BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE, id VARCHAR(500) PRIMARY KEY,
          medicine VARCHAR(500) NOT NULL, patient_id VARCHAR(500) NOT NULL REFERENCES patients(id),
          staff_id VARCHAR(500) NOT NULL REFERENCES accounts(id), quantity INT NOT NULL CHECK(quantity>0),
          expiry DATE NOT NULL, remarks CLOB NOT NULL, dispensed_at TIMESTAMP(9) NOT NULL,
          strength VARCHAR(100) NOT NULL DEFAULT '', formulation VARCHAR(100) NOT NULL DEFAULT '',
          visit_id VARCHAR(500) REFERENCES visits(id), campus_id VARCHAR(500),
          UNIQUE(id, patient_id));
        CREATE TABLE IF NOT EXISTS notifications (
          seq BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE, id VARCHAR(500) PRIMARY KEY,
          patient_id VARCHAR(500) NOT NULL REFERENCES patients(id), transaction_id VARCHAR(500) NOT NULL,
          reason VARCHAR(500) NOT NULL, created_at TIMESTAMP(9) NOT NULL,
          status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','SENT')),
          FOREIGN KEY(transaction_id,patient_id) REFERENCES dispensing(id,patient_id));
        CREATE INDEX IF NOT EXISTS visit_patient_time ON visits(patient_id,visit_time);
        CREATE INDEX IF NOT EXISTS audit_time ON audit_entries(event_time);
        """;
}
