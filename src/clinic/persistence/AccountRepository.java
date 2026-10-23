package clinic.persistence;

import clinic.model.*;
import clinic.service.AdminService;
import java.util.List;

/** Password hashes are available only to the authentication component, never to UI models. */
public final class AccountRepository {
    private final Database database;
    public AccountRepository(Database database) { this.database=database; }
    public record Account(User user, String hash, boolean active, int failures, long lockedUntil) {}
    public List<Account> load() {
        return database.transaction(c -> Sql.query(c,"SELECT * FROM accounts ORDER BY id", r ->
            new Account(AdminService.user(r.getString("id"),r.getString("name"),r.getString("role")),
                r.getString("password_hash"),r.getBoolean("active"),r.getInt("failed_attempts"),r.getLong("locked_until"))));
    }
    public void insert(User user, String hash) {
        database.transaction(c -> Sql.update(c,"INSERT INTO accounts(id,name,role,password_hash,active) VALUES(?,?,?,?,?)",
            user.getUserId(),user.getName(),user.getRole(),hash,true));
    }
    public void update(User user, boolean active) {
        database.transaction(c -> {
            if(Sql.update(c,"UPDATE accounts SET name=?,role=?,active=? WHERE id=?",
                user.getName(),user.getRole(),active,user.getUserId())!=1) throw new IllegalArgumentException("User not found.");
            return null;
        });
    }
    public void attempts(String id, int count, long until) {
        database.transaction(c -> Sql.update(c,"UPDATE accounts SET failed_attempts=?,locked_until=? WHERE id=?",count,until,id));
    }
}
