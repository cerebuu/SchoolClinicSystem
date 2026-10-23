package clinic.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// This class is responsible for storing clinic audit entries in memory.
public class AuditLog {

    private final List<AuditEntry> entries = new ArrayList<AuditEntry>();

    public synchronized void record(LocalDateTime timestamp, String userId,
                                    String userName, String role, String action,
                                    String details, boolean allowed) {
        entries.add(new AuditEntry(timestamp, userId, userName, role, action,
                details, allowed));
    }

    public synchronized List<AuditEntry> getAll() {
        return new ArrayList<AuditEntry>(entries);
    }

    public synchronized List<AuditEntry> getForUser(String userId) {
        List<AuditEntry> result = new ArrayList<AuditEntry>();
        for (AuditEntry entry : entries) {
            if (same(userId, entry.getUserId())) {
                result.add(entry);
            }
        }
        return result;
    }

    public synchronized List<AuditEntry> getDenied() {
        List<AuditEntry> result = new ArrayList<AuditEntry>();
        for (AuditEntry entry : entries) {
            if (!entry.isAllowed()) {
                result.add(entry);
            }
        }
        return result;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized PageResult<AuditEntry> page(int page, int size, String query) {
        String[] tokens = query.toLowerCase(java.util.Locale.ROOT).trim().split("\\s+");
        return PageResult.select(entries, page, size, e -> {
            for (String token : tokens) {
                if (token.equals("denied")) { if (e.isAllowed()) return false; }
                else if (!(e.getUserId()+" "+e.getTimestamp()+" "+e.getAction()).toLowerCase(java.util.Locale.ROOT).contains(token)) return false;
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
