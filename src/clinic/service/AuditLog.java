package clinic.service;

import java.time.LocalDateTime;
import java.time.LocalDate;
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

    /** Durable callers combine their business changes and audit append in one database transaction. */
    public synchronized <T> T unitOfWork(java.util.function.Supplier<T> work) {
        java.util.Objects.requireNonNull(work);
        return repository==null?work.get():repository.unitOfWork(work);
    }

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
        return repository == null ? new ArrayList<>(entries) : repository.audit();
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
        if(repository!=null)return repository.auditPage(page,size,tokens);
        List<AuditEntry> filtered=getAll().stream().filter(e -> {
            for (String raw : tokens) {
                FilterToken token=FilterToken.parse(raw);
                if (token.field().isEmpty()&&token.value().equals("denied")) {
                    if(e.isAllowed())return false;
                    continue;
                }
                String searchable=switch(token.field()) {
                    case "user"->e.getUserId();
                    case "campus"->String.valueOf(e.getCampusId());
                    case "action"->e.getAction();
                    case "date"->String.valueOf(e.getTimestamp());
                    default->e.getUserId()+" "+e.getCampusId()+" "+e.getTimestamp()+" "+e.getAction();
                };
                if(!searchable.toLowerCase(java.util.Locale.ROOT).contains(token.value()))return false;
            }
            return true;
        }).toList();
        return PageResult.select(TimeOrder.newestFirst(filtered,AuditEntry::getTimestamp),
            page,size,ignored->true);
    }

    public synchronized AuditSummary summary(LocalDate date) {
        if(repository!=null) {
            var result=repository.auditSummary(date);
            return new AuditSummary(result.eventsToday(),result.denied());
        }
        return new AuditSummary(entries.stream()
            .filter(entry->entry.getTimestamp().toLocalDate().equals(date)).count(),
            entries.stream().filter(entry->!entry.isAllowed()).count());
    }

    public record AuditSummary(long eventsToday,long denied) {}

    private boolean same(String first, String second) {
        if (first == null) {
            return second == null;
        }
        return first.equals(second);
    }

    private record FilterToken(String field,String value) {
        private static FilterToken parse(String raw) {
            String value=raw.toLowerCase(java.util.Locale.ROOT);
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
