package clinic.persistence;

import java.sql.*;
import java.util.*;

/** Expands allowed notification states while retaining every existing row and constraint. */
final class MySqlNotificationSchema {
    private record Check(String name,String clause) {}
    private MySqlNotificationSchema() {}
    private static List<Check> checks(Connection c) throws SQLException {
        return Sql.query(c,"""
            SELECT tc.CONSTRAINT_NAME,cc.CHECK_CLAUSE
            FROM information_schema.TABLE_CONSTRAINTS tc
            JOIN information_schema.CHECK_CONSTRAINTS cc
              ON tc.CONSTRAINT_SCHEMA=cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME=cc.CONSTRAINT_NAME
            WHERE tc.CONSTRAINT_SCHEMA=DATABASE() AND tc.TABLE_NAME='notifications'
              AND tc.CONSTRAINT_TYPE='CHECK'
            """,r->new Check(r.getString(1),r.getString(2)));
    }
    private static List<Check> restrictive(Connection c) throws SQLException {
        return checks(c).stream().filter(check->check.clause().toLowerCase(Locale.ROOT).matches("(?s).*\\bstatus\\b.*")
            &&!check.clause().toUpperCase(Locale.ROOT).contains("'FAILED'")).toList();
    }
    static void preflight(Connection c) throws SQLException {
        if(Sql.mariadb(c)&&!restrictive(c).isEmpty())
            throw new SchemaException("The MariaDB notifications status CHECK excludes FAILED. A DBA must widen this CHECK to PENDING, SENT, FAILED first. Automatic migration will not drop constraints or disable all checks.");
    }
    static void migrate(Connection c) throws SQLException {
        // ADD the replacement before disabling only the obsolete check. Retry is safe after either DDL.
        ensureCheck(c,"notification_status_check","status IN ('PENDING','SENT','FAILED')");
        for(Check check:restrictive(c))
            MySqlMetadata.execute(c,"ALTER TABLE notifications ALTER CHECK `"+check.name().replace("`","``")+"` NOT ENFORCED");
        ensureCheck(c,"notification_message_type_check","message_type IN ('CLINIC_VISIT','MEDICINE_GIVEN','SENT_HOME','FOLLOW_UP_NEEDED','EMERGENCY')");
        var nullable=Sql.query(c,"SELECT IS_NULLABLE FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='notifications' AND COLUMN_NAME='transaction_id'",
            r->r.getString(1));
        if(!nullable.equals(List.of("YES"))) {
            String type=MySqlMetadata.idType(c,"dispensing");
            MySqlMetadata.execute(c,"ALTER TABLE notifications MODIFY COLUMN transaction_id "+type+" NULL");
        }
    }
    private static void ensureCheck(Connection c,String name,String clause) throws SQLException {
        if(checks(c).stream().noneMatch(check->check.name().equalsIgnoreCase(name)))
            MySqlMetadata.execute(c,"ALTER TABLE notifications ADD CONSTRAINT "+name+" CHECK("+clause+")");
    }
}
