import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.PageHeader;
import clinic.app.ui.notifications.NotificationsPanel;
import clinic.model.SchoolNurse;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

public final class NotificationsTitleDemo {
    private NotificationsTitleDemo() {}

    public static void main(String[] args) throws Exception {
        Theme.install();
        ApplicationContext app=new ApplicationContext(false);
        try {
            app.login.bootstrapAdministrator("root","Demo administrator","test-password");
            app.login.login("root","test-password").orElseThrow();
            app.admin.saveCampus(new clinic.model.Campus("C1","Campus 1"),false);
            SchoolNurse nurse=new SchoolNurse("N1","Demo nurse");nurse.setCampusId("C1");
            app.admin.addUser(nurse,"test-password");
            app.login.logout();
            app.login.login("N1","test-password").orElseThrow();
            SwingUtilities.invokeAndWait(()->printScreen(app));
        } finally {
            app.close();
        }
    }

    private static void printScreen(ApplicationContext app) {
        NotificationsPanel panel=new NotificationsPanel(app);
        PageHeader header=find(panel,PageHeader.class);
        List<JLabel> labels=new ArrayList<>();
        collect(header,labels);
        String title=labels.isEmpty()?"<missing>":labels.get(0).getText();
        JTabbedPane tabs=find(panel,JTabbedPane.class);
        List<String> tabNames=new ArrayList<>();
        if(tabs!=null)for(int index=0;index<tabs.getTabCount();index++)tabNames.add(tabs.getTitleAt(index));
        System.out.println("Screen title | "+title);
        System.out.println("Inner tabs | "+String.join(" | ",tabNames));
        System.out.println("Popup titles | "+String.join(" | ",NotificationsPanel.popupTitles()));
    }

    private static void collect(Container container,List<JLabel> labels) {
        for(Component child:container.getComponents()) {
            if(child instanceof JLabel label)labels.add(label);
            if(child instanceof Container nested)collect(nested,labels);
        }
    }

    private static <T> T find(Container container,Class<T> type) {
        if(type.isInstance(container))return type.cast(container);
        for(Component child:container.getComponents())if(child instanceof Container nested) {
            T found=find(nested,type);
            if(found!=null)return found;
        }
        return null;
    }
}
