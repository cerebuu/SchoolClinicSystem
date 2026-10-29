package clinic.persistence;

import clinic.model.Campus;
import clinic.service.AuditEntry;
import clinic.service.PageResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AdminRepository {
    private final Database database;
    public AdminRepository(Database database) { this.database=database; }
    /** Joins clinic data and audit writes against this database in one commit. */
    public <T> T unitOfWork(java.util.function.Supplier<T> work) {
        return database.transaction(c->work.get());
    }
    public List<Campus> campuses() {
        return database.transaction(c -> Sql.query(c,"SELECT * FROM campuses ORDER BY id", r -> new Campus(r.getString("id"),r.getString("name"))));
    }
    /** Local development databases receive defaults; remote campus catalogs remain authoritative. */
    public void installDefaultCampus(Campus campus) {
        if(!database.isRemote())save(campus,false);
    }
    public void save(Campus campus, boolean edit) {
        database.transaction(c -> {
            if(edit) {
                if(Sql.update(c,"UPDATE campuses SET name=? WHERE id=?",campus.name(),campus.id())!=1)
                    throw new IllegalArgumentException("Campus not found.");
            } else Sql.update(c,"INSERT INTO campuses VALUES(?,?)",campus.id(),campus.name());
            return null;
        });
    }
    public void deleteCampus(String campusId) {
        database.transaction(c->{
            if(Sql.update(c,"DELETE FROM campuses WHERE id=?",campusId)!=1)
                throw new IllegalArgumentException("Campus not found.");
            return null;
        });
    }
    public void append(AuditEntry entry) {
        database.transaction(c -> Sql.update(c,"INSERT INTO audit_entries(event_time,user_id,user_name,role,action,details,allowed,campus_id) VALUES(?,?,?,?,?,?,?,?)",
            entry.getTimestamp(),entry.getUserId(),entry.getUserName(),entry.getRole(),entry.getAction(),entry.getDetails(),entry.isAllowed(),entry.getCampusId()));
    }
    public List<AuditEntry> audit() {
        return database.transaction(c -> Sql.query(c,"SELECT * FROM audit_entries ORDER BY seq", r -> new AuditEntry(
            r.getObject("event_time",java.time.LocalDateTime.class),r.getString("user_id"),r.getString("user_name"),
            r.getString("role"),r.getString("action"),r.getString("details"),r.getBoolean("allowed"),
            r.getString("campus_id"))));
    }

    public PageResult<AuditEntry> auditPage(int page,int size,String[] tokens) {
        if(page<0||size<1)throw new IllegalArgumentException("Invalid page.");
        long offset=(long)page*size;
        return database.transaction(c -> {
            StringBuilder where=new StringBuilder();
            List<Object> parameters=new ArrayList<>();
            for(String raw:tokens) {
                where.append(where.length()==0?" WHERE ":" AND ");
                FilterToken token=FilterToken.parse(raw);
                if(token.field().isEmpty()&&token.value().equals("denied"))where.append("allowed=FALSE");
                else {
                    String searchable=switch(token.field()) {
                        case "user"->"user_id";
                        case "campus"->"COALESCE(campus_id,'null')";
                        case "action"->"action";
                        case "date"->"REPLACE("+Sql.text(c,"event_time")+",' ','T')";
                        default->"CONCAT(user_id,' ',COALESCE(campus_id,'null'),' ',REPLACE("
                            +Sql.text(c,"event_time")+",' ','T'),' ',action)";
                    };
                    where.append("POSITION(? IN LOWER(").append(searchable).append("))>0");
                    parameters.add(token.value());
                }
            }
            String filter=where.toString();
            long count=Sql.query(c,"SELECT COUNT(*) FROM audit_entries"+filter,r->r.getLong(1),parameters.toArray()).get(0);
            parameters.add(size);
            parameters.add(offset);
            List<AuditEntry> rows=Sql.query(c,"SELECT * FROM audit_entries"+filter+" ORDER BY event_time DESC,seq DESC LIMIT ? OFFSET ?",
                r->new AuditEntry(r.getObject("event_time",LocalDateTime.class),r.getString("user_id"),
                    r.getString("user_name"),r.getString("role"),r.getString("action"),
                    r.getString("details"),r.getBoolean("allowed"),r.getString("campus_id")),parameters.toArray());
            return new PageResult<>(rows,Math.toIntExact(count),page,size);
        });
    }

    public AuditSummary auditSummary(LocalDate date) {
        LocalDateTime from=date.atStartOfDay(),to=date.plusDays(1).atStartOfDay();
        return database.transaction(c->Sql.query(c,"""
            SELECT COALESCE(SUM(CASE WHEN event_time>=? AND event_time<? THEN 1 ELSE 0 END),0),
                   COALESCE(SUM(CASE WHEN allowed=FALSE THEN 1 ELSE 0 END),0)
            FROM audit_entries
            """,r->new AuditSummary(r.getLong(1),r.getLong(2)),from,to).get(0));
    }

    public record AuditSummary(long eventsToday,long denied) {}

    private record FilterToken(String field,String value) {
        private static FilterToken parse(String raw) {
            String value=raw.toLowerCase(Locale.ROOT);
            int colon=value.indexOf(':');
            if(colon>0) {
                String field=value.substring(0,colon);
                if(java.util.Set.of("user","campus","action","date").contains(field))
                    return new FilterToken(field,value.substring(colon+1));
            }
            return new FilterToken("",value);
        }
    }
}
