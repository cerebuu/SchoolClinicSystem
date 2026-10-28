package clinic.persistence;

import clinic.model.*;
import clinic.service.AdminService;
import java.util.List;

/** Password hashes are available only to the authentication component, never to UI models. */
public final class AccountRepository {
    private final Database database;
    public AccountRepository(Database database) { this.database=database; }
    public <T> T unitOfWork(java.util.function.Supplier<T> work) {
        return database.transaction(c->work.get());
    }
    public record Account(User user, String hash, boolean active, int failures, long lockedUntil) {}
    public List<Account> load() {
        return database.transaction(c -> Sql.query(c,"SELECT * FROM accounts ORDER BY id", r -> {
            User user=AdminService.user(r.getString("id"),r.getString("name"),r.getString("role"));
            user.setCampusId(r.getString("campus_id"));
            return new Account(user,r.getString("password_hash"),r.getBoolean("active"),
                r.getInt("failed_attempts"),r.getLong("locked_until"));
        }));
    }
    public void insert(User user, String hash) {
        database.transaction(c -> Sql.update(c,"INSERT INTO accounts(id,name,role,password_hash,active,campus_id) VALUES(?,?,?,?,?,?)",
            user.getUserId(),user.getName(),user.getRole(),hash,true,user.getCampusId()));
    }
    public void update(User user, boolean active) {
        database.transaction(c -> {
            if(Sql.update(c,"UPDATE accounts SET name=?,role=?,active=?,campus_id=? WHERE id=?",
                user.getName(),user.getRole(),active,user.getCampusId(),user.getUserId())!=1)
                throw new IllegalArgumentException("User not found.");
            return null;
        });
    }
    public void attempts(String id, int count, long until) {
        database.transaction(c -> Sql.update(c,"UPDATE accounts SET failed_attempts=?,locked_until=? WHERE id=?",count,until,id));
    }
}
