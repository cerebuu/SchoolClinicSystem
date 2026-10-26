import clinic.app.*;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.MessagePopup;
import clinic.app.ui.inventory.*;
import clinic.app.ui.dispense.*;
import clinic.app.ui.dashboard.*;
import clinic.app.ui.notifications.*;
import clinic.app.ui.patients.*;
import clinic.app.ui.consultations.*;
import clinic.model.*;
import clinic.service.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import javax.swing.*;

/** Visible event-driven demo with six real views open together; synthetic fixture only. */
public final class LiveRefreshDemo {
    private static JFrame frame;
    private static int checks;
    static void edt(Runnable work)throws Exception{SwingUtilities.invokeAndWait(work);}
    static List<Component> all(Container root){List<Component> found=new ArrayList<>();for(Component c:root.getComponents()){found.add(c);if(c instanceof Container n)found.addAll(all(n));}return found;}
    static PagedTablePanel<?> table(Container root){return all(root).stream().filter(c->c instanceof PagedTablePanel<?>).map(c->(PagedTablePanel<?>)c).findFirst().orElseThrow();}
    static JTable grid(Container root){return all(root).stream().filter(c->c instanceof JTable).map(c->(JTable)c).findFirst().orElseThrow();}
    static PagedTablePanel<?> byModel(Container root,String model){return all(root).stream().filter(c->c instanceof PagedTablePanel<?> t
        &&t.modelType().equals(model))
        .map(c->(PagedTablePanel<?>)c).findFirst().orElseThrow();}
    static void check(String message,boolean ok){if(!ok)throw new AssertionError(message);System.out.println("PASS: "+message);checks++;}
    static void await(java.util.function.BooleanSupplier done)throws Exception{
        for(int i=0;i<200;i++){AtomicBoolean ready=new AtomicBoolean();edt(()->ready.set(done.getAsBoolean()));if(ready.get())return;Thread.sleep(50);}throw new AssertionError("Refresh timed out");
    }
    static int stock(JTable table,String name){int total=0;for(int r=0;r<table.getRowCount();r++)if(name.equals(table.getValueAt(r,0)))total+=((Number)table.getValueAt(r,1)).intValue();return total;}
    static boolean outbreak(Container root){return all(root).stream().filter(c->c instanceof JTable).map(c->(JTable)c).anyMatch(t->{for(int r=0;r<t.getRowCount();r++)if("OUTBREAK".equals(t.getValueAt(r,0)))return true;return false;});}
    public static void main(String[] args)throws Exception{
        try(ApplicationContext app=new ApplicationContext(true)){
            app.login.login("N001","nurse123").orElseThrow();
            for(int i=1;i<=3;i++){Student p=new Student("p"+i,"Fixture "+i,"contact","CS",1,"A");p.setGuardianContact("guardian");app.clinic().registerPatient(p);}
            app.clinic().addMedicine("paracetamol",12,LocalDate.now().plusDays(90),"");
            JPanel[] panels=new JPanel[6];
            edt(()->{
                Theme.install();frame=new JFrame("Automatic refresh demo");frame.setLayout(new GridLayout(2,3));
                panels[0]=new InventoryPanel(app);panels[1]=new TransactionHistoryPanel(app);panels[2]=new NurseDashboardPanel(app);
                panels[3]=new NotificationsPanel(app);panels[4]=new PatientsPanel(app);panels[5]=new ConsultationsPanel(app);
                for(JPanel panel:panels)frame.add(panel);frame.setSize(1800,1000);frame.setVisible(true);
            });
            await(()->all(frame).stream().filter(c->c instanceof PagedTablePanel<?> p&&p.isShowing()).allMatch(c->all((Container)c).stream()
                .anyMatch(l->l instanceof JLabel label&&label.getText().equals("Updated just now"))));
            JTabbedPane notificationTabs=all(panels[3]).stream().filter(c->c instanceof JTabbedPane).map(c->(JTabbedPane)c).findFirst().orElseThrow();
            edt(()->notificationTabs.setSelectedIndex(1));
            await(()->byModel(panels[3],"AlertsTableModel").isShowing());
            AtomicReference<PagedTablePanel<?>> prediction=new AtomicReference<>();
            edt(()->prediction.set(table((Container)all(panels[2]).stream().filter(c->c instanceof MedicinePredictionsPanel).findFirst().orElseThrow())));
            edt(()->System.out.println("Before dispense: inventory="+stock(grid(panels[0]),"paracetamol")+", transactions="+table(panels[1]).totalCount()
                +", predictions="+grid(prediction.get()).getValueAt(0,2)+", alerts="+all(panels[3]).stream().filter(c->c instanceof PagedTablePanel<?>).map(c->((PagedTablePanel<?>)c).totalCount()).toList()));
            DispenseResult result=app.dispenseSession().dispense("p1",List.of(new DispenseItem("paracetamol",4)),false);
            SwingUtilities.invokeLater(()->MessagePopup.info(frame,result.report()));
            await(()->stock(grid(panels[0]),"paracetamol")==8&&table(panels[1]).totalCount()==1
                &&Integer.valueOf(8).equals(grid(prediction.get()).getValueAt(0,1)));
            edt(()->{
                check("Updates run behind open success popup",Arrays.stream(Window.getWindows()).anyMatch(w->w instanceof JDialog&&w.isShowing()));
                System.out.println("After dispense: inventory="+stock(grid(panels[0]),"paracetamol")+", transactions="+table(panels[1]).totalCount()+", predictions="+grid(prediction.get()).getValueAt(0,2));
                JTable alertGrid=grid((Container)byModel(panels[3],"AlertsTableModel"));
                List<String> alertRows=new ArrayList<>();for(int r=0;r<alertGrid.getRowCount();r++)alertRows.add(alertGrid.getValueAt(r,0)+": "+alertGrid.getValueAt(r,1));
                System.out.println("Alerts after dispense: "+alertRows);
                for(Window w:Window.getWindows())if(w instanceof JDialog)w.dispose();
            });
            await(()->byModel(panels[3],"AlertsTableModel").totalCount()==1);
            check("Dispense publishes inventory, history, predictions and stock alert updates",true);
            app.restockSession("paracetamol").accept(Map.of("Quantity","10","Expiry (YYYY-MM-DD)",LocalDate.now().plusDays(120).toString(),"Remarks","delivery"));
            await(()->stock(grid(panels[0]),"paracetamol")==18);
            app.clinic().addMedicine("new medicine",20,LocalDate.now().plusDays(90),"");
            await(()->table(panels[0]).totalCount()==3&&all(panels[3]).stream().filter(c->c instanceof PagedTablePanel<?>).allMatch(t->((PagedTablePanel<?>)t).totalCount()==0));
            System.out.println("After restock/add: paracetamol=18; inventory batches=3; stock alerts=0");
            for(int i=1;i<=3;i++)app.clinic().recordVisit("p"+i,List.of("Fever"),"Visit","","N001");
            await(()->outbreak(panels[3])&&table(panels[5]).totalCount()==3);
            check("Outbreak automatically appears in Notifications alerts",true);
            app.clinic().createGuardianNotification("p1",result.completed().get(0).getTransactionId(),"medicine given");
            edt(()->notificationTabs.setSelectedIndex(0));await(()->byModel(panels[3],"NotificationsTableModel").totalCount()==1);
            check("Guardian notification automatically appears",true);
            app.clinic().registerPatient(new Employee("new","Fixture employee","contact","Office","Staff"));
            await(()->table(panels[4]).totalCount()==4);System.out.println("Patients before=3, after=4");
            Thread.sleep(500);AtomicInteger publications=new AtomicInteger();
            var subscription=app.changes.subscribe(publications::incrementAndGet,DataTopic.INVENTORY,DataTopic.TRANSACTIONS,DataTopic.ALERTS);
            var failed=app.dispenseSession().dispense("p1",List.of(new DispenseItem("paracetamol",999)),false);
            Thread.sleep(500);check("Failed dispense publishes no data changes",!failed.successful()&&publications.get()==0);subscription.close();
            System.out.println("Failed dispense: "+failed.failure());
            int[] before=new int[3];edt(()->{before[0]=table(panels[0]).refreshCount();before[1]=prediction.get().refreshCount();before[2]=table(panels[4]).refreshCount();});
            for(int i=0;i<20;i++)app.clinic().addMedicine("Burst "+i,20,LocalDate.now().plusDays(90),"");
            await(()->table(panels[0]).totalCount()==23);Thread.sleep(500);
            edt(()->{int inv=table(panels[0]).refreshCount()-before[0],pred=prediction.get().refreshCount()-before[1],patients=table(panels[4]).refreshCount()-before[2];
                System.out.println("20 actions refresh counts: Inventory="+inv+", Predictions="+pred+", Patients="+patients);
                check("Burst coalesces affected panels only",inv<5&&pred<5&&patients==0);
            });
            int[] closed={0};edt(()->{closed[0]=table(panels[0]).refreshCount();frame.dispose();});
            app.login.logout();app.changes.publish(DataTopic.values());Thread.sleep(350);
            check("Logout removes all subscriptions",app.changes.subscriberCount()==0);
            edt(()->check("Closed panels receive no refresh",closed[0]==table(panels[0]).refreshCount()));
            System.out.println("PASS: "+checks+" live workflow checks.");
        }finally{edt(()->{for(Window w:Window.getWindows())w.dispose();});}
    }
}
