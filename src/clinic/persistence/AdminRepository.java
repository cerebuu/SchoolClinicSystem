package clinic.persistence;

import clinic.model.Campus;
import clinic.service.AuditEntry;
import java.util.List;

public final class AdminRepository {
    private final Database database;
    public AdminRepository(Database database) { this.database=database; }
    public List<Campus> campuses() {
        return database.transaction(c -> Sql.query(c,"SELECT * FROM campuses ORDER BY id", r -> new Campus(r.getString("id"),r.getString("name"))));
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
    public void append(AuditEntry entry) {
        database.transaction(c -> Sql.update(c,"INSERT INTO audit_entries(event_time,user_id,user_name,role,action,details,allowed) VALUES(?,?,?,?,?,?,?)",
            entry.getTimestamp(),entry.getUserId(),entry.getUserName(),entry.getRole(),entry.getAction(),entry.getDetails(),entry.isAllowed()));
    }
    public List<AuditEntry> audit() {
        return database.transaction(c -> Sql.query(c,"SELECT * FROM audit_entries ORDER BY seq", r -> new AuditEntry(
            r.getObject("event_time",java.time.LocalDateTime.class),r.getString("user_id"),r.getString("user_name"),
            r.getString("role"),r.getString("action"),r.getString("details"),r.getBoolean("allowed"))));
    }
}
