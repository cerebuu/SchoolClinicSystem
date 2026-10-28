package clinic.persistence;

import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.Map;

/** The same temporary-table assertions run on H2 and on the configured MySQL server. */
public final class JdbcValueChecks {
    public static void main(String[] args) throws Exception {
        Path fallback=Files.createTempDirectory("scms-jdbc-").resolve("clinic");
        try(Database db=Database.configured(fallback,Map.of("SCMS_DB_URL"," "))) {
            if(db.isRemote())throw new AssertionError("Blank URL must retain H2.");
            run(db);
        }
        try { Database.configured(fallback,Map.of("SCMS_DB_URL","jdbc:unknown://example")); throw new AssertionError("URL"); }
        catch(IllegalArgumentException expected) { /* Fail before opening any local database. */ }
        try { Database.configured(fallback,Map.of("SCMS_DB_URL","jdbc:mysql://example/test")); throw new AssertionError("credentials"); }
        catch(IllegalArgumentException expected) { /* Require explicit credentials; empty passwords are allowed. */ }
        System.out.println("PASS: H2 fallback/configuration and JDBC value checks.");
    }
    static void run(Database db) {
        db.transaction(c->{
            boolean mysql=Sql.mysql(c);
            String identity=mysql?"BIGINT AUTO_INCREMENT":"BIGINT GENERATED ALWAYS AS IDENTITY";
            MySqlMetadata.execute(c,"CREATE "+(mysql?"":"LOCAL ")+"TEMPORARY TABLE scms_value_check (seq "+identity
                +" PRIMARY KEY,note "+(mysql?"TEXT":"CLOB")+",at_time "+(mysql?"DATETIME(6)":"TIMESTAMP(9)")
                +",on_date DATE,flag BOOLEAN)");
            LocalDateTime time=LocalDateTime.of(2026,10,28,14,15,16,123456789);
            LocalDate date=LocalDate.of(2026,10,28);
            String note="Unicode clinic note — ñ "+"long text ".repeat(1000);
            Sql.update(c,"INSERT INTO scms_value_check(note,at_time,on_date,flag) VALUES(?,?,?,?)",note,time,date,true);
            try(PreparedStatement s=c.prepareStatement("INSERT INTO scms_value_check(flag) VALUES(?)",Statement.RETURN_GENERATED_KEYS)) {
                s.setBoolean(1,false);s.executeUpdate();
                try(ResultSet keys=s.getGeneratedKeys()) {
                    if(!keys.next()||keys.getLong(1)!=2)throw new AssertionError("generated key");
                }
            }
            Sql.query(c,"SELECT * FROM scms_value_check ORDER BY seq LIMIT ? OFFSET ?",r->{
                LocalDateTime expected=mysql?time.withNano(123456000):time;
                if(!note.equals(r.getString("note"))||!expected.equals(r.getObject("at_time",LocalDateTime.class))
                        ||!date.equals(r.getObject("on_date",LocalDate.class))||!r.getBoolean("flag"))
                    throw new AssertionError("text/date/time/boolean round trip");
                return true;
            },1,0);
            if(Sql.query(c,"SELECT COUNT(*) FROM scms_value_check WHERE flag=FALSE",r->r.getInt(1)).get(0)!=1)
                throw new AssertionError("boolean predicate");
            if(!Sql.query(c,"SELECT CONCAT('a',' ','b'),"+Sql.text(c,"at_time")+" FROM scms_value_check WHERE seq=1",
                    r->r.getString(1)).equals(java.util.List.of("a b")))throw new AssertionError("portable expressions");
            MySqlMetadata.execute(c,"DROP "+(mysql?"TEMPORARY ":"")+"TABLE scms_value_check");
            System.out.println("PASS: "+c.getMetaData().getDatabaseProductName()+" generated keys, Unicode text, LocalDateTime, DATE, BOOLEAN, CONCAT, casts, LIMIT/OFFSET.");
            return null;
        });
    }
}
