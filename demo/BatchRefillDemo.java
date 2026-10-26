import clinic.app.*;
import clinic.app.ui.common.PagedTablePanel;
import clinic.app.ui.inventory.InventoryPanel;
import clinic.app.ui.reports.ReportsPanel;
import clinic.model.*;
import clinic.service.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import javax.swing.text.JTextComponent;

/** Desktop verification for zero-stock batch reuse and Reports live refresh. */
public final class BatchRefillDemo {
    private static final AuditLog audit=new AuditLog();
    private static ApplicationContext app;
    private static JFrame frame;
    private static final LocalDate OLD_EXPIRY=LocalDate.of(2049,12,13);
    private static final LocalDate NEW_EXPIRY=LocalDate.of(2051,12,13);
    private static List<Component> components(Container root){List<Component> out=new ArrayList<>();for(Component c:root.getComponents()){out.add(c);if(c instanceof Container x)out.addAll(components(x));}return out;}
    private static void edt(Runnable r)throws Exception{SwingUtilities.invokeAndWait(r);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    private static void waitFor(java.util.function.BooleanSupplier test)throws Exception{long until=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<until){AtomicReference<Boolean> ok=new AtomicReference<>(false);edt(()->ok.set(test.getAsBoolean()));if(ok.get())return;Thread.sleep(25);}throw new AssertionError("Timed out waiting for UI state");}
    private static JDialog dialog(String title){return Arrays.stream(Window.getWindows()).filter(w->w instanceof JDialog d&&d.isShowing()&&title.equals(d.getTitle())).map(w->(JDialog)w).findFirst().orElse(null);}
    private static void click(Container root,String label){components(root).stream().filter(c->c instanceof JButton b&&b.isShowing()&&label.equals(b.getText())).map(c->(JButton)c).findFirst().orElseThrow().doClick();}
    private static void input(Container root,String label,String value){components(root).stream().filter(c->c instanceof JTextComponent&&c instanceof JComponent j&&label.equals(j.getAccessibleContext().getAccessibleName())).map(c->(JTextComponent)c).findFirst().orElseThrow().setText(value);}
    private static JTable table(){return components(frame).stream().filter(c->c instanceof JTable&&c.isShowing()).map(c->(JTable)c).findFirst().orElseThrow();}
    private static PagedTablePanel<?> paged(){return components(frame).stream().filter(c->c instanceof PagedTablePanel<?>).map(c->(PagedTablePanel<?>)c).findFirst().orElseThrow();}
    private static List<Medicine> rows(String name){return app.medicines(0,100,name).rows().stream().filter(m->BatchMatcher.sameName(m.getName(),name)).toList();}
    private static List<Medicine> allRows(){return app.medicines(0,100,"").rows();}
    private static void print(String title,String name){System.out.println(title+": "+rows(name).stream().map(m->m.getBatchId()+" qty="+m.getQuantity()+" expiry="+m.getExpirationDate()+" batch="+m.getBatchDate()+" remarks=["+m.getRemarks()+"]").toList());}
    private static JDialog restockForm(){return Arrays.stream(Window.getWindows()).filter(w->w instanceof JDialog d&&d.isShowing()&&d.getTitle().startsWith("Restock ")).map(w->(JDialog)w).findFirst().orElseThrow();}
    private static void openRestock(String batchId)throws Exception{SwingUtilities.invokeLater(()->{if(batchId==null)table().clearSelection();else{List<Medicine> rows=allRows();int index=-1;for(int i=0;i<rows.size();i++)if(rows.get(i).getBatchId().equals(batchId))index=i;if(index<0)throw new AssertionError("batch not visible");table().setRowSelectionInterval(index,index);}click(frame,"Restock");});waitFor(()->Arrays.stream(Window.getWindows()).anyMatch(w->w instanceof JDialog d&&d.isShowing()&&d.getTitle().startsWith("Restock ")));}
    private static void submitRestock(String batchId,String medicine,int quantity,LocalDate expiry,String remarks,String choice)throws Exception{
        int before=paged().refreshCount();openRestock(batchId);JDialog form=restockForm();edt(()->{if(batchId==null)input(form,"Medicine",medicine);input(form,"Quantity",Integer.toString(quantity));input(form,"Expiry (YYYY-MM-DD)",expiry.toString());input(form,"Remarks",remarks);click(form,"Save");});
        waitFor(()->dialog("Reuse empty batch?")!=null);System.out.println("Reuse confirmation shown; choose "+choice+" for "+medicine);edt(()->click(dialog("Reuse empty batch?"),choice));
        if(choice.equals("Cancel")){waitFor(()->form.isShowing());return;}
        waitFor(()->!form.isShowing());waitFor(()->dialog("School clinic")!=null);edt(()->click(dialog("School clinic"),"OK"));waitFor(()->dialog("School clinic")==null);waitFor(()->paged().refreshCount()>before);
    }
    private static Medicine addZero(String name,String remarks){return addZero(name,remarks,LocalDate.now());}
    private static Medicine addZero(String name,String remarks,LocalDate batchDate){try{Field storeField=ApplicationContext.class.getDeclaredField("store");storeField.setAccessible(true);InMemoryClinicSystem store=(InMemoryClinicSystem)storeField.get(app);Field inventoryField=InMemoryClinicSystem.class.getDeclaredField("inventory");inventoryField.setAccessible(true);Inventory inventory=(Inventory)inventoryField.get(store);Medicine batch=new Medicine(name,0,OLD_EXPIRY,batchDate,remarks);inventory.addMedicine(batch);return rows(name).stream().filter(m->m.getBatchId().equals(batch.getBatchId())).findFirst().orElseThrow();}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static void printTopics(){
        System.out.println("Table refresh topics:");
        System.out.println("Reports=VISITS; Patients=PATIENTS; Consultations=VISITS; Notifications=NOTIFICATIONS; Alerts=ALERTS");
        System.out.println("Dashboard cards=PATIENTS,VISITS,INVENTORY; Recent visits=VISITS,PATIENTS; Users=USERS; Campuses=CAMPUSES; Audit Log=AUDIT");
        System.out.println("Inventory=INVENTORY; Dispense history=TRANSACTIONS; Medicine picker=INVENTORY; Medicine predictions=INVENTORY,TRANSACTIONS");
    }
    private static void reportsRefresh()throws Exception{
        app.clinic().registerPatient(new Student("report-p","Report Fixture","contact","BSCS",1,"A"));
        for(int i=0;i<27;i++)app.clinic().recordVisit("report-p",List.of("Headache"),"target visit "+i,"","N001");
        edt(()->frame.setContentPane(new ReportsPanel(app)));
        waitFor(()->paged().totalCount()==27);PagedTablePanel<?> reports=paged();reports.applyFilter("target");waitFor(()->reports.totalCount()==27);reports.setPage(1);waitFor(()->reports.currentPage()==1&&reports.getComponentCount()>0);
        waitFor(()->table().getRowCount()==2);edt(()->table().setRowSelectionInterval(0,0));String selected=((MedicalRecord)reports.selected()).getRecordId();
        List<MedicalRecord> before=app.records(1,25,"target").rows();System.out.println("Reports page 2 before consultation: "+before.stream().map(MedicalRecord::getRecordId).toList());
        int refresh=reports.refreshCount();app.clinic().recordVisit("report-p",List.of("Cough"),"target visit 27","","N001");
        waitFor(()->reports.refreshCount()>refresh&&reports.totalCount()==28&&reports.currentPage()==1&&"target".equals(reports.filterText()));
        waitFor(()->table().getRowCount()==3);List<MedicalRecord> after=app.records(1,25,"target").rows();System.out.println("Reports page 2 after consultation, no Refresh click: "+after.stream().map(MedicalRecord::getRecordId).toList());
        check(reports.currentPage()==1&&"target".equals(reports.filterText()),"Reports stays on page 2 with its filter");
        check(((MedicalRecord)reports.selected()).getRecordId().equals(selected),"Reports selected row remains selected");
    }
    public static void main(String[] args)throws Exception{
        if(GraphicsEnvironment.isHeadless())throw new IllegalStateException("A desktop session is required.");
        app=new ApplicationContext(true,audit);app.login.login("N001","nurse123").orElseThrow();
        Medicine oldest= new Medicine("zero",0,NEW_EXPIRY,LocalDate.now().minusDays(20),"old");
        Medicine newest= new Medicine("zero",0,NEW_EXPIRY,LocalDate.now().minusDays(2),"new");
        check(BatchMatcher.depleted(List.of(oldest,newest)," ZERO ",null).orElseThrow().getBatchId().equals(newest.getBatchId()),"no-selection picker chooses most recent zero batch");
        Medicine selectedStock=new Medicine("zero",5,NEW_EXPIRY,LocalDate.now(),"stocked");
        check(BatchMatcher.depleted(List.of(oldest,selectedStock),"zero",selectedStock.getBatchId()).isEmpty(),"selected nonzero row does not redirect refill to another empty batch");
        Medicine first=addZero("no-selection-zero","old zero",LocalDate.now().minusDays(1));addZero("no-selection-zero","latest zero",LocalDate.now());
        Medicine newestZero=BatchMatcher.depleted(rows("no-selection-zero"),"no-selection-zero",null).orElseThrow();
        System.out.println("No-selection empty batch chosen: "+newestZero.getBatchId()+" batchDate="+newestZero.getBatchDate());
        Medicine selectedFirst=addZero("selected-zero","first row");Medicine selectedSecond=addZero("selected-zero","selected row");
        Medicine createOld=addZero("create-zero","empty");Medicine cancelOld=addZero("cancel-zero","empty");
        edt(()->{Theme.install();frame=new JFrame("Batch refill verification");frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);frame.setSize(1000,700);frame.setContentPane(new InventoryPanel(app));frame.setVisible(true);});
        waitFor(()->table().getRowCount()>=6);
        print("No-selection before","no-selection-zero");submitRestock(null,"no-selection-zero",20,NEW_EXPIRY,"refilled latest","Reuse empty batch");print("No-selection after","no-selection-zero");
        check(rows("no-selection-zero").size()==2&&rows("no-selection-zero").stream().anyMatch(m->m.getBatchId().equals(newestZero.getBatchId())&&m.getQuantity()==20&&m.getExpirationDate().equals(NEW_EXPIRY)&&m.getBatchDate().equals(LocalDate.now())&&m.getRemarks().equals("refilled latest")),"no-selection refill updates most recent batch in place");
        print("Selected row before","selected-zero");submitRestock(selectedSecond.getBatchId(),"selected-zero",15,NEW_EXPIRY,"selected remarks","Reuse empty batch");print("Selected row after","selected-zero");
        check(rows("selected-zero").size()==2&&rows("selected-zero").stream().anyMatch(m->m.getBatchId().equals(selectedSecond.getBatchId())&&m.getQuantity()==15)&&rows("selected-zero").stream().anyMatch(m->m.getBatchId().equals(selectedFirst.getBatchId())&&m.getQuantity()==0),"selected zero row is reused by its exact batch ID");
        int count=rows("create-zero").size();submitRestock(createOld.getBatchId(),"create-zero",7,NEW_EXPIRY,"new batch","Create new batch");check(rows("create-zero").size()==count+1&&rows("create-zero").stream().anyMatch(m->m.getBatchId().equals(createOld.getBatchId())&&m.getQuantity()==0),"Create new batch leaves the empty row and adds a row");
        count=rows("cancel-zero").size();submitRestock(cancelOld.getBatchId(),"cancel-zero",8,NEW_EXPIRY,"cancel remarks","Cancel");check(rows("cancel-zero").size()==count&&rows("cancel-zero").get(0).getQuantity()==0,"Cancel changes no batch");check("8".equals(components(restockForm()).stream().filter(c->c instanceof JTextField&&c instanceof JComponent j&&"Quantity".equals(j.getAccessibleContext().getAccessibleName())).map(c->((JTextField)c).getText()).findFirst().orElse("")),"Cancel preserves restock values");edt(()->click(restockForm(),"Cancel"));
        Medicine expiredStock=new Medicine("expired-stock",5,LocalDate.now().minusDays(1),LocalDate.now().minusDays(20),"keep");
        InMemoryClinicSystem memory=new InMemoryClinicSystem(new InMemoryClinicSystem.State(List.of(),List.of(),List.of(),List.of(),List.of(expiredStock)));
        System.out.println("Expired stock before refill attempt: "+memory.medicinePage(0,10,"").rows());
        try{memory.refillBatch(expiredStock.getBatchId(),4,NEW_EXPIRY,"must not replace");throw new AssertionError("stock batch refill should fail");}catch(IllegalStateException e){check("Batch still has stock".equals(e.getMessage()),"expired batch with stock cannot be reused");}
        System.out.println("Expired stock after refill attempt: "+memory.medicinePage(0,10,"").rows());
        app.login.logout();app.login.login("A001","assist123").orElseThrow();try{app.refillBatchSession(first.getBatchId(),"no-selection-zero").accept(Map.of("Quantity","1","Expiry (YYYY-MM-DD)",NEW_EXPIRY.toString(),"Remarks","private remark"));throw new AssertionError("assistant refill unexpectedly allowed");}catch(SecurityException e){System.out.println("PASS assistant cannot refill a batch");}
        check(audit.getAll().stream().filter(e->e.getAction().equals("restock reused empty batch")).noneMatch(e->e.getDetails().contains("refilled latest")||e.getDetails().contains("selected remarks")||e.getDetails().contains("private remark")),"refill audit has medicine details and no remarks");
        app.login.logout();app.login.login("N001","nurse123").orElseThrow();reportsRefresh();printTopics();
        edt(()->frame.dispose());app.close();System.out.println("BatchRefillDemo complete.");
    }
}
