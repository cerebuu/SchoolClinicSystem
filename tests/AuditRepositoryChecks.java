import clinic.persistence.AdminRepository;
import clinic.persistence.Database;
import clinic.core.Login;
import clinic.service.AdminService;
import clinic.service.AuditEntry;
import clinic.service.AuditLog;
import clinic.service.PageResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Compares bounded SQL audit queries with the in-memory audit behavior. */
public final class AuditRepositoryChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path folder=Files.createTempDirectory("scms-audit-check-");
        try {
            LocalDate base=LocalDate.of(2026,1,1);
            LocalDate summaryDate=base.plusDays(1);
            try(Database database=new Database(folder.resolve("audit"))) {
                AuditLog memory=new AuditLog();
                AuditLog persistent=new AuditLog(new AdminRepository(database));
                for(int i=0;i<65;i++) {
                    LocalDateTime time=base.plusDays(i%3).atTime(9,15).plusSeconds(i);
                    String user=String.format("user-%03d",i);
                    String campus=i%2==0?"C1":null;
                    boolean allowed=i%5!=0;
                    AuditEntry entry=new AuditEntry(time,user,"Person "+i,"NURSE",
                        i%4==0?"recordVisit":"viewAudit", "details",allowed,campus);
                    memory.record(time,user,entry.getUserName(),entry.getRole(),entry.getAction(),
                        entry.getDetails(),allowed,campus);
                    persistent.record(time,user,entry.getUserName(),entry.getRole(),entry.getAction(),
                        entry.getDetails(),allowed,campus);
                }

                for(String query:List.of("","USER-04","c1 2026-01-02","denied",
                        "USER-04 denied","recordvisit","viewAudit C1","null","T09:15",
                        "user:user-000","user:C1","user:recordvisit","campus:user-000",
                        "action:user-000","action:record action:visit","date:2026-01-02",
                        "user:user-000 denied")) {
                    for(int page:List.of(0,1,6,20)) {
                        compare(memory.page(page,10,query),persistent.page(page,10,query),
                            "query='"+query+"' page="+page);
                    }
                }

                check(memory.page(0,100,"user:C1").total()==0
                    &&persistent.page(0,100,"user:C1").total()==0,
                    "user filter does not match an overlapping campus value");
                check(memory.page(0,100,"user:recordvisit").total()==0
                    &&persistent.page(0,100,"user:recordvisit").total()==0,
                    "user filter does not match an overlapping action value");
                check(memory.page(0,100,"action:user-000").total()==0
                    &&persistent.page(0,100,"action:user-000").total()==0,
                    "action filter does not match an overlapping user value");
                check(memory.page(0,100,"action:record action:visit").total()==17
                    &&persistent.page(0,100,"action:record action:visit").total()==17,
                    "multiword action filter applies AND within the action column");
                check(memory.page(0,100,"date:2026-01-02").total()==22
                    &&persistent.page(0,100,"date:2026-01-02").total()==22,
                    "date field filter is limited to the event date");
                check(memory.page(0,100,"user:user-000 denied").total()==1
                    &&persistent.page(0,100,"user:user-000 denied").total()==1,
                    "typed actor filter combines with the denied token");

                AuditLog.AuditSummary expected=memory.summary(summaryDate);
                AuditLog.AuditSummary actual=persistent.summary(summaryDate);
                check(expected.equals(actual),"SQL summary equals in-memory event and denied counts");
                check(actual.eventsToday()==22,"summary counts only the requested calendar day");
                check(actual.denied()==13,"summary counts all denied events");

                Login login=new Login();
                login.bootstrapAdministrator("admin","Administrator","password");
                login.login("admin","password").orElseThrow();
                AdminService admin=new AdminService(login,persistent,new AdminRepository(database));
                AdminService.Summary dashboard=admin.summary();
                AuditLog.AuditSummary todaySummary=persistent.summary(LocalDate.now());
                check(dashboard.eventsToday()==todaySummary.eventsToday()&&dashboard.denied()==todaySummary.denied(),
                    "admin dashboard summary uses SQL aggregates");

                rejects(IllegalArgumentException.class,()->persistent.page(-1,10,""),"negative page rejected");
                rejects(IllegalArgumentException.class,()->persistent.page(0,0,""),"zero page size rejected");
            }
        } finally {
            try(var paths=Files.walk(folder)) {
                for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
            }
        }
        System.out.println("PASS: "+checks+" SQL audit page, filter, ordering, and summary checks.");
    }

    private static void compare(PageResult<AuditEntry> expected,PageResult<AuditEntry> actual,String label) {
        check(expected.total()==actual.total(),label+" total");
        check(expected.page()==actual.page()&&expected.size()==actual.size(),label+" page metadata");
        check(expected.rows().size()==actual.rows().size(),label+" row count");
        for(int i=0;i<expected.rows().size();i++) {
            AuditEntry left=expected.rows().get(i),right=actual.rows().get(i);
            check(left.getTimestamp().equals(right.getTimestamp())
                &&left.getUserId().equals(right.getUserId())
                &&left.getUserName().equals(right.getUserName())
                &&left.getRole().equals(right.getRole())
                &&left.getAction().equals(right.getAction())
                &&left.getDetails().equals(right.getDetails())
                &&left.isAllowed()==right.isAllowed()
                &&java.util.Objects.equals(left.getCampusId(),right.getCampusId()),label+" row "+i);
        }
    }

    private static void rejects(Class<? extends Throwable> type,Runnable action,String label) {
        try { action.run(); }
        catch(Throwable error) {
            if(type.isInstance(error)) { checks++; return; }
            throw new AssertionError(label,error);
        }
        throw new AssertionError(label);
    }

    private static void check(boolean condition,String label) {
        if(!condition)throw new AssertionError(label);
        checks++;
    }
}
