package clinic.persistence;

import java.sql.*;
import java.util.*;

/** Upgrades an existing MySQL/MariaDB v5+ database; never creates or seeds base tables. */
final class MySqlSchema {
    private MySqlSchema() {}
    static void migrate(Connection c) throws SQLException {
        MySqlMetadata.require(c,"schema_version","id,version");
        var versions=Sql.query(c,"SELECT version FROM schema_version WHERE id=1",r->r.getInt(1));
        if(versions.size()!=1||versions.get(0)<5||versions.get(0)>14)
            throw new SchemaException("Expected an existing MySQL/MariaDB schema_version from 5 through 14. No database is created or reseeded.");
        int version=versions.get(0);
        requireBase(c);
        if(version>=7)MySqlMetadata.require(c,"dispensing_batch_allocations","transaction_id,batch_id,quantity,expiry");
        if(version>=10)MySqlMetadata.require(c,"inventory_movements","seq,id,event_type,medicine,quantity,expiry,batch_id,event_time,actor_id,campus_id");
        if(version>=13) {
            MySqlMetadata.require(c,"alert_states","alert_key,scope_key,fingerprint,active,generation");
            MySqlMetadata.require(c,"alert_seen","user_id,alert_key,generation");
        }
        MySqlNotificationSchema.preflight(c);
        if(version==5) {
            add(c,"notifications","message_type","VARCHAR(30) NOT NULL DEFAULT 'MEDICINE_GIVEN'");
            add(c,"notifications","channel","VARCHAR(10)");
            add(c,"notifications","attempt_count","INT NOT NULL DEFAULT 0");
            add(c,"notifications","last_attempt_at","DATETIME(6)");
            MySqlNotificationSchema.migrate(c);
            version=advance(c,6);
        }
        if(version==6) {
            for(String table:List.of("medicine_batches","dispensing")) {
                add(c,table,"strength","VARCHAR(100) NOT NULL DEFAULT ''");
                add(c,table,"formulation","VARCHAR(100) NOT NULL DEFAULT ''");
            }
            add(c,"dispensing","visit_id",MySqlMetadata.idType(c,"visits")+" NULL");
            MySqlMetadata.execute(c,"CREATE TABLE IF NOT EXISTS dispensing_batch_allocations (transaction_id "
                +MySqlMetadata.idType(c,"dispensing")+" NOT NULL, batch_id VARCHAR(36) NOT NULL,"
                +" quantity INT NOT NULL CHECK(quantity>0), expiry DATE NOT NULL, PRIMARY KEY(transaction_id,batch_id),"
                +" FOREIGN KEY(transaction_id) REFERENCES dispensing(id)) ENGINE=InnoDB");
            version=advance(c,7);
        }
        if(version==7) {
            add(c,"accounts","campus_id","VARCHAR(500)");
            add(c,"audit_entries","campus_id","VARCHAR(500)");
            version=advance(c,8);
        }
        if(version==8) {
            add(c,"visits","campus_id","VARCHAR(500)");
            add(c,"dispensing","campus_id","VARCHAR(500)");
            version=advance(c,9);
        }
        if(version==9) {
            MySqlMetadata.execute(c,"CREATE TABLE IF NOT EXISTS inventory_movements ("
                +"seq BIGINT AUTO_INCREMENT PRIMARY KEY,id VARCHAR(500) NOT NULL UNIQUE,"
                +"event_type VARCHAR(30) NOT NULL CHECK(event_type IN ('INITIAL_STOCK','RESTOCK','REFILL')),"
                +"medicine VARCHAR(500) NOT NULL,strength VARCHAR(100) NOT NULL DEFAULT '',"
                +"formulation VARCHAR(100) NOT NULL DEFAULT '',quantity INT NOT NULL CHECK(quantity>0),"
                +"expiry DATE NOT NULL,batch_id VARCHAR(36),event_time DATETIME(6) NOT NULL,actor_id "
                +MySqlMetadata.idType(c,"accounts")+" NOT NULL,campus_id VARCHAR(500) NOT NULL,"
                +"FOREIGN KEY(actor_id) REFERENCES accounts(id)) ENGINE=InnoDB");
            MySqlMetadata.index(c,"inventory_movements","inventory_movement_campus_time","campus_id,event_time");
            MySqlMetadata.index(c,"inventory_movements","inventory_movement_medicine_time","medicine,event_time");
            version=advance(c,10);
        }
        if(version==10) {
            add(c,"notifications","campus_id","VARCHAR(500)");
            MySqlMetadata.index(c,"notifications","notification_campus_time","campus_id,created_at");
            version=advance(c,11);
        }
        if(version==11) {
            for(String table:List.of("medicine_batches","dispensing","inventory_movements")) {
                add(c,table,"atc_primary","VARCHAR(500) NOT NULL DEFAULT ''");
                add(c,table,"atc_secondary","VARCHAR(500) NOT NULL DEFAULT ''");
            }
            advance(c,12);
            version=12;
        }
        if(version==12) {
            MySqlMetadata.execute(c,"CREATE TABLE IF NOT EXISTS alert_states (alert_key VARCHAR(64) PRIMARY KEY,"
                +"scope_key VARCHAR(500) NOT NULL,fingerprint VARCHAR(64) NOT NULL,active BOOLEAN NOT NULL,"
                +"generation BIGINT NOT NULL) ENGINE=InnoDB");
            MySqlMetadata.execute(c,"CREATE TABLE IF NOT EXISTS alert_seen (user_id "
                +MySqlMetadata.idType(c,"accounts")+" NOT NULL,alert_key VARCHAR(64) NOT NULL,generation BIGINT NOT NULL,"
                +"PRIMARY KEY(user_id,alert_key)) ENGINE=InnoDB");
            advance(c,13);
            version=13;
        }
        if(version==13) {
            for(String table:List.of("medicine_batches","dispensing","inventory_movements")) {
                add(c,table,"atc_primary","VARCHAR(500) NOT NULL DEFAULT ''");
                add(c,table,"atc_secondary","VARCHAR(500) NOT NULL DEFAULT ''");
                for(String column:List.of("strength","formulation")) {
                    if(MySqlMetadata.column(c,table,column))
                        MySqlMetadata.execute(c,"ALTER TABLE "+table+" ALTER COLUMN "+column+" SET DEFAULT ''");
                }
            }
            advance(c,14);
        }
    }
    private static void add(Connection c,String table,String column,String type) throws SQLException {
        MySqlMetadata.add(c,table,column,type);
    }
    private static int advance(Connection c,int version) throws SQLException {
        Sql.update(c,"UPDATE schema_version SET version=? WHERE id=1",version);
        return version;
    }
    private static void requireBase(Connection c) throws SQLException {
        Map<String,String> tables=new LinkedHashMap<>();
        tables.put("accounts","id,name,role,password_hash,active,failed_attempts,locked_until");
        tables.put("campuses","id,name");
        tables.put("audit_entries","seq,event_time,user_id,user_name,role,action,details,allowed");
        tables.put("patients","id,name,contact,patient_type,course_department,section_position,year_level,guardian_contact");
        tables.put("allergies","patient_id,allergy");
        tables.put("patient_history","patient_id,ordinal,detail");
        tables.put("visits","seq,id,patient_id,reason,assessment,medication,staff_id,visit_time");
        tables.put("symptoms","visit_id,ordinal,symptom");
        tables.put("medicine_batches","ordinal,name,quantity,expiry,batch_date,remarks");
        tables.put("medicine_batch_ids","ordinal,batch_id");
        tables.put("dispensing","seq,id,medicine,patient_id,staff_id,quantity,expiry,remarks,dispensed_at");
        tables.put("notifications","seq,id,patient_id,transaction_id,reason,created_at,status,recipient_label,recipient_contact");
        for(var table:tables.entrySet())MySqlMetadata.require(c,table.getKey(),table.getValue());
    }
}
