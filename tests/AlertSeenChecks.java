import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.nav.NurseShell;
import clinic.app.nav.SidebarItem;
import clinic.core.ClinicSystem;
import clinic.model.Alert;
import clinic.model.Student;
import clinic.persistence.ClinicRepository;
import clinic.persistence.Database;
import clinic.service.ClinicData;
import clinic.service.InMemoryClinicSystem;
import java.awt.Color;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

public final class AlertSeenChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        memoryTrackingAndReactivation();
        notificationPageMarksVisibleRows();
        badgePresentation();
        persistentSeenState();
        System.out.println("PASS: "+checks+" alert badge, per-user seen, reactivation, page-view, and persistence checks.");
    }

    private static void memoryTrackingAndReactivation() {
        InMemoryClinicSystem data=new InMemoryClinicSystem();
        List<String> ids=addOutbreakPatients(data,3,"A");
        check(data.unseenAlertCount("N001","uic-bajada")==1,"new outbreak starts unseen for first user");
        List<Alert> visible=data.alertPage(0,25,"","uic-bajada").rows();
        data.markAlertsSeen("N001",visible);
        check(data.unseenAlertCount("N001","uic-bajada")==0,"viewing a row clears it for that user");
        check(data.unseenAlertCount("N002","uic-bajada")==1,"another user retains an independent unseen alert");
        data.markAlertsSeen("N002",visible);
        check(data.unseenAlertCount("N002","uic-bajada")==0,"second user's view clears only their state");

        ids.addAll(addOutbreakPatients(data,1,"A"));
        check(data.unseenAlertCount("N001","uic-bajada")==1,"outbreak count growth raises a new badge");
        check(data.unseenAlertCount("N002","uic-bajada")==1,"outbreak update is unseen by each user");
        List<Alert> updated=data.alertPage(0,25,"","uic-bajada").rows();
        data.markAlertsSeen("N001",updated);
        data.markAlertsSeen("N002",updated);
        check(data.unseenAlertCount("N001","uic-bajada")==0,"updated outbreak clears after viewing");

        for(String id:ids)data.updatePatient(id,new Student(id,"Patient "+id,"contact","CS",1,"B"));
        List<Alert> newGroup=data.alertPage(0,25,"","uic-bajada").rows();
        data.markAlertsSeen("N001",newGroup);
        check(data.unseenAlertCount("N001","uic-bajada")==0,"new active group can be marked seen");
        for(String id:ids)data.updatePatient(id,new Student(id,"Patient "+id,"contact","CS",1,"A"));
        check(data.unseenAlertCount("N001","uic-bajada")==1,"reactivated alert is unseen after its prior view");
    }

    private static List<String> addOutbreakPatients(ClinicData data,int count,String section) {
        List<String> ids=new ArrayList<>();
        for(int index=0;index<count;index++) {
            String id="alert-patient-"+java.util.UUID.randomUUID();
            ids.add(id);
            data.registerPatient(new Student(id,"Patient "+id,"contact","CS",1,section));
            data.recordVisitWithCampus(id,List.of("Fever"),"Concern","","N001","uic-bajada");
        }
        return ids;
    }

    private static void notificationPageMarksVisibleRows() throws Exception {
        try(ApplicationContext app=new ApplicationContext(true)) {
            app.login.login("N001","nurse123").orElseThrow();
            ClinicSystem clinic=app.clinic();
            for(int index=0;index<3;index++) {
                addClinicVisit(clinic,index);
            }
            check(app.unseenAlertCount()==1,"sidebar source reports one unseen alert before opening");
            AtomicReference<NurseShell> shellRef=new AtomicReference<>();
            SwingUtilities.invokeAndWait(()->shellRef.set(new NurseShell(app,()->{})));
            SwingUtilities.invokeAndWait(()->findSidebar(shellRef.get(),"Notifications").doClick());
            awaitCount(app,0);
            check(app.unseenAlertCount()==0,"opening Notifications marks displayed alert rows seen");
            addClinicVisit(clinic,3);
            awaitCount(app,0);
            check(app.unseenAlertCount()==0,"alert arriving while Notifications is active is seen after table refresh");
            SwingUtilities.invokeAndWait(()->findNavigator(shellRef.get()).open("Nurse dashboard"));
            addClinicVisit(clinic,4);
            Thread.sleep(300);
            check(app.unseenAlertCount()==1,"alert arriving while Notifications is inactive remains unseen");
            app.login.logout();
            app.login.login("N001","nurse123").orElseThrow();
            check(app.unseenAlertCount()==1,"in-memory unseen state survives logout and login");
        }
    }

    private static void addClinicVisit(ClinicSystem clinic,int index) {
        String id="page-alert-"+index;
        clinic.registerPatient(new Student(id,"Patient "+index,"contact","CS",1,"A"));
        clinic.recordVisit(id,List.of("Fever"),"Concern","","N001");
    }

    private static SidebarItem findSidebar(Container root,String label) {
        for(var component:root.getComponents()) {
            if(component instanceof SidebarItem item&&item.getText().equals(label))return item;
            if(component instanceof Container child) {
                try { return findSidebar(child,label); }
                catch(IllegalArgumentException ignored) { }
            }
        }
        throw new IllegalArgumentException("Sidebar item not found: "+label);
    }

    private static clinic.app.nav.Navigator findNavigator(Container root) {
        for(var component:root.getComponents()) {
            if(component instanceof clinic.app.nav.Navigator navigator)return navigator;
            if(component instanceof Container child) {
                try { return findNavigator(child); }
                catch(IllegalArgumentException ignored) { }
            }
        }
        throw new IllegalArgumentException("Navigator not found.");
    }

    private static void awaitCount(ApplicationContext app,int expected) throws Exception {
        long deadline=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<deadline&&app.unseenAlertCount()!=expected)Thread.sleep(25);
    }

    private static void badgePresentation() throws Exception {
        AtomicReference<Throwable> failure=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            try {
                Theme.install();
                SidebarItem item=new SidebarItem("Notifications");
                item.setSize(204,44);
                check(item.badgeText().isEmpty()&&item.getToolTipText()==null,"badge is hidden at zero");
                item.setBadgeCount(1);
                check(item.badgeText().equals("1")&&item.getToolTipText().equals("1 new alert"),"one alert has singular tooltip");
                item.setBadgeCount(9);
                check(item.badgeText().equals("9"),"badge shows nine without capping");
                item.setBadgeCount(10);
                check(item.badgeText().equals("9+")&&item.getToolTipText().equals("10 new alerts"),"badge caps display above nine");
                BufferedImage image=new BufferedImage(204,44,BufferedImage.TYPE_INT_ARGB);
                var graphics=image.createGraphics();
                item.paint(graphics);
                graphics.dispose();
                boolean dangerPixel=false;
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
                    if(new Color(image.getRGB(x,y),true).equals(Theme.DANGER))dangerPixel=true;
                check(dangerPixel,"badge uses danger theme color");
                item.setBadgeCount(0);
                check(item.badgeText().isEmpty()&&item.getToolTipText()==null,"cleared badge is hidden");
            } catch(Throwable error) { failure.set(error); }
        });
        if(failure.get()!=null)throw new AssertionError("Badge checks failed",failure.get());
    }

    private static void persistentSeenState() throws Exception {
        Path directory=Files.createTempDirectory("clinic-alert-seen-");
        Path base=directory.resolve("clinic");
        try {
            try(Database database=new Database(base)) {
                seedAccount(database);
                ClinicData data=ClinicRepository.create(database);
                addOutbreakPatients(data,3,"A");
                check(data.unseenAlertCount("N001","uic-bajada")==1,"persisted new alert is unseen");
                data.markAlertsSeen("N001",data.alertPage(0,25,"","uic-bajada").rows());
                check(data.unseenAlertCount("N001","uic-bajada")==0,"persisted alert view clears badge");
            }
            try(Database database=new Database(base)) {
                ClinicData data=ClinicRepository.create(database);
                check(data.unseenAlertCount("N001","uic-bajada")==0,"seen state survives database restart");
                check(database.transaction(connection->{
                    try(var query=connection.createStatement().executeQuery("SELECT version FROM schema_version WHERE id=1")) {
                        return query.next()?query.getInt(1):0;
                    }
                })==14,"additive H2 migration advances to schema version 14");
            }
        } finally {
            try(var paths=Files.walk(directory)) {
                for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(path);
            }
        }
    }

    private static void seedAccount(Database database) {
        database.transaction(connection->{
            try(var insert=connection.prepareStatement("INSERT INTO accounts(id,name,role,password_hash,active,failed_attempts,locked_until,campus_id) VALUES(?,?,?,?,?,?,?,?)")) {
                insert.setString(1,"N001");insert.setString(2,"Test nurse");insert.setString(3,"NURSE");
                insert.setString(4,"fixture");insert.setBoolean(5,true);insert.setInt(6,0);insert.setLong(7,0);
                insert.setString(8,"uic-bajada");insert.executeUpdate();
            }
            return null;
        });
    }

    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
        checks++;
    }
}
