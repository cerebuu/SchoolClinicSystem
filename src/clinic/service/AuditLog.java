package clinic.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// This class is responsible for storing clinic audit entries in memory.
public class AuditLog {

    private final List<AuditEntry> entries = new ArrayList<AuditEntry>();
    private final clinic.persistence.AdminRepository repository;
    private Runnable onRecorded=()->{};
    public synchronized void onRecorded(Runnable listener){onRecorded=java.util.Objects.requireNonNull(listener);}
    public AuditLog() { repository=null; }
    public AuditLog(clinic.persistence.AdminRepository repository) { this.repository=repository; }

    public synchronized void record(LocalDateTime timestamp, String userId,
                                    String userName, String role, String action,
                                    String details, boolean allowed) {
        record(timestamp,userId,userName,role,action,details,allowed,null);
    }
    public synchronized void record(LocalDateTime timestamp,String userId,String userName,String role,
            String action,String details,boolean allowed,String campusId) {
        AuditEntry entry = new AuditEntry(timestamp,userId,userName,role,action,details,allowed,campusId);
        if (repository != null) repository.append(entry); else entries.add(entry);
        onRecorded.run();
    }

    public synchronized List<AuditEntry> getAll() {
        return repository == null ? new ArrayList<AuditEntry>(entries) : repository.audit();
    }

    public synchronized List<AuditEntry> getForUser(String userId) {
        List<AuditEntry> result = new ArrayList<AuditEntry>();
        for (AuditEntry entry : getAll()) {
            if (same(userId, entry.getUserId())) {
                result.add(entry);
            }
        }
        return result;
    }

    public synchronized List<AuditEntry> getDenied() {
        List<AuditEntry> result = new ArrayList<AuditEntry>();
        for (AuditEntry entry : getAll()) {
            if (!entry.isAllowed()) {
                result.add(entry);
            }
        }
        return result;
    }

    public synchronized int size() {
        return getAll().size();
    }

    public synchronized void clear() {
        if (repository != null) throw new UnsupportedOperationException("Persistent audit history cannot be cleared.");
        entries.clear();
    }

    public synchronized PageResult<AuditEntry> page(int page, int size, String query) {
        String[] tokens = query.toLowerCase(java.util.Locale.ROOT).trim().split("\\s+");
        return PageResult.select(getAll(), page, size, e -> {
            for (String token : tokens) {
                if (token.equals("denied")) { if (e.isAllowed()) return false; }
                else if (!(e.getUserId()+" "+e.getCampusId()+" "+e.getTimestamp()+" "+e.getAction())
                        .toLowerCase(java.util.Locale.ROOT).contains(token)) return false;
            }
            return true;
        });
    }
    private boolean same(String first, String second) {
        if (first == null) {
            return second == null;
        }
        return first.equals(second);
    }
}
