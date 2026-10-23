package clinic.persistence;

import java.sql.*;

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
                    if (version < 1 || version > 2) throw new SQLException("Unsupported database schema version.");
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
            }
        }
    }
    private static final String DDL = """
        CREATE TABLE IF NOT EXISTS accounts (
          id VARCHAR(500) PRIMARY KEY, name VARCHAR(500) NOT NULL,
          role VARCHAR(20) NOT NULL CHECK(role IN ('ADMIN','NURSE','ASSISTANT','DOCTOR')),
          password_hash VARCHAR(200) NOT NULL, active BOOLEAN NOT NULL);
        CREATE TABLE IF NOT EXISTS campuses (id VARCHAR(500) PRIMARY KEY, name VARCHAR(500) NOT NULL);
        CREATE TABLE IF NOT EXISTS audit_entries (
          seq BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, event_time TIMESTAMP(9) NOT NULL,
          user_id VARCHAR(500) NOT NULL, user_name VARCHAR(500) NOT NULL, role VARCHAR(20) NOT NULL,
          action VARCHAR(100) NOT NULL, details VARCHAR(500) NOT NULL, allowed BOOLEAN NOT NULL);
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
          assessment CLOB NOT NULL, medication CLOB NOT NULL, staff_id VARCHAR(500) NOT NULL REFERENCES accounts(id), visit_time TIMESTAMP(9) NOT NULL);
        CREATE TABLE IF NOT EXISTS symptoms (
          visit_id VARCHAR(500) REFERENCES visits(id), ordinal INT NOT NULL, symptom VARCHAR(500) NOT NULL, PRIMARY KEY(visit_id,ordinal));
        CREATE TABLE IF NOT EXISTS medicine_batches (
          ordinal INT PRIMARY KEY, name VARCHAR(500) NOT NULL, quantity INT NOT NULL CHECK(quantity>=0),
          expiry DATE NOT NULL, batch_date DATE NOT NULL, remarks CLOB NOT NULL);
        CREATE TABLE IF NOT EXISTS dispensing (
          seq BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE, id VARCHAR(500) PRIMARY KEY,
          medicine VARCHAR(500) NOT NULL, patient_id VARCHAR(500) NOT NULL REFERENCES patients(id),
          staff_id VARCHAR(500) NOT NULL REFERENCES accounts(id), quantity INT NOT NULL CHECK(quantity>0),
          expiry DATE NOT NULL, remarks CLOB NOT NULL, dispensed_at TIMESTAMP(9) NOT NULL,
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
