import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.inventory.InventoryPanel;
import clinic.model.Medicine;
import clinic.service.BatchMatcher;
import clinic.service.AuditLog;
import java.awt.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import javax.swing.text.JTextComponent;

/** Automated desktop demonstration of both medicine batch entry paths. */
public final class DuplicateBatchDemo {
    private static ApplicationContext app;
    private static final AuditLog audit=new AuditLog();
    private static JFrame frame;
    private static InventoryPanel panel;
    private static final LocalDate expiry=LocalDate.of(2049,12,13);
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);System.out.println("PASS "+label);}
    private static List<Component> components(Container parent){List<Component> all=new ArrayList<>();for(Component c:parent.getComponents()){all.add(c);if(c instanceof Container x)all.addAll(components(x));}return all;}
    private static void edt(Runnable run)throws Exception{SwingUtilities.invokeAndWait(run);}
    private static void waitFor(java.util.function.BooleanSupplier condition)throws Exception{long end=System.currentTimeMillis()+12000;while(System.currentTimeMillis()<end){AtomicReference<Boolean> ok=new AtomicReference<>(false);edt(()->ok.set(condition.getAsBoolean()));if(ok.get())return;Thread.sleep(25);}throw new AssertionError("UI wait timed out");}
    private static JDialog dialog(String title){return Arrays.stream(Window.getWindows()).filter(w->w instanceof JDialog d&&d.isShowing()&&d.getTitle().equals(title)).map(w->(JDialog)w).findFirst().orElse(null);}
    private static void click(Container root,String text){components(root).stream().filter(c->c instanceof JButton b&&b.isShowing()&&b.getText().equals(text)).map(c->(JButton)c).findFirst().orElseThrow().doClick();}
    private static void input(Container root,String name,String value){components(root).stream().filter(c->c instanceof JTextComponent&&c instanceof JComponent j&&name.equals(j.getAccessibleContext().getAccessibleName())).map(c->(JTextComponent)c).findFirst().orElseThrow().setText(value);}
    private static int refreshCount(){return components(frame).stream().filter(c->c instanceof clinic.app.ui.common.PagedTablePanel<?>).map(c->((clinic.app.ui.common.PagedTablePanel<?>)c).refreshCount()).findFirst().orElse(0);}
    private static void openForm(boolean restock)throws Exception{SwingUtilities.invokeLater(()->{if(restock){JTable t=components(frame).stream().filter(c->c instanceof JTable&&c.isShowing()).map(c->(JTable)c).findFirst().orElseThrow();if(t.getRowCount()==0)throw new AssertionError("inventory empty");t.setRowSelectionInterval(0,0);click(frame,"Restock");}else click(frame,"Add medicine");});waitFor(()->Arrays.stream(Window.getWindows()).anyMatch(w->w instanceof JDialog d&&d.isShowing()&&(d.getTitle().startsWith("Restock ")||d.getTitle().equals("Add medicine batch"))));}
    private static JDialog form() {return Arrays.stream(Window.getWindows()).filter(w->w instanceof JDialog d&&d.isShowing()&&(d.getTitle().startsWith("Restock ")||d.getTitle().equals("Add medicine batch"))).map(w->(JDialog)w).findFirst().orElseThrow();}
    private static void submit(boolean restock,String name,int qty,LocalDate date,String remarks,String choice)throws Exception{
        int refreshBefore=refreshCount();openForm(restock);JDialog form=form();edt(()->{if(!restock)input(form,"Medicine",name);input(form,"Quantity",Integer.toString(qty));input(form,"Expiry (YYYY-MM-DD)",date.toString());input(form,"Remarks",remarks);click(form,"Save");});
        boolean match=date.equals(expiry)&&BatchMatcher.sameName(name,"paracetamol");
        if(match){waitFor(()->dialog("Matching medicine batch")!=null);System.out.println("Popup displayed: "+name+" expiry="+date+"; selected "+choice);edt(()->click(dialog("Matching medicine batch"),choice));
            if(choice.equals("Cancel")){waitFor(()->form.isShowing());return;}}
        else System.out.println("No duplicate popup: "+name+" expiry="+date);
        waitFor(()->!form.isShowing());
        if(restock){waitFor(()->dialog("School clinic")!=null);edt(()->click(dialog("School clinic"),"OK"));waitFor(()->dialog("School clinic")==null);}
        waitFor(()->refreshCount()>refreshBefore);
    }
    private static List<Medicine> rows(){return app.medicines(0,100,"").rows();}
    private static void print(String label){System.out.println(label+": "+rows().stream().map(m->m.getName()+" qty="+m.getQuantity()+" expiry="+m.getExpirationDate()+" batch="+m.getBatchDate()+" remarks=["+m.getRemarks()+"]").toList());}
    public static void main(String[] args)throws Exception{
        if(GraphicsEnvironment.isHeadless())throw new IllegalStateException("Run this demo in a desktop session.");
        app=new ApplicationContext(true,audit);app.login.login("N001","nurse123").orElseThrow();
        app.clinic().addMedicine("paracetamol",12,expiry,"original");
        edt(()->{Theme.install();frame=new JFrame("Duplicate batch verification");frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);frame.setSize(1000,650);panel=new InventoryPanel(app);frame.setContentPane(panel);frame.setVisible(true);});
        waitFor(()->!rows().isEmpty());print("Before merge");int refresh=refreshCount();
        submit(true,"paracetamol",12,expiry,"merge note","Merge quantities");print("After merge");check(rows().size()==1&&rows().get(0).getQuantity()==24,"restock merge keeps one row at quantity 24");
        submit(true,"paracetamol",12,expiry,"separate note","Keep separate batch");print("After keep separate");check(rows().size()==2,"keep separate creates a second batch");
        int before=rows().stream().mapToInt(Medicine::getQuantity).sum();submit(true,"paracetamol",12,expiry,"cancel note","Cancel");check(rows().stream().mapToInt(Medicine::getQuantity).sum()==before,"cancel leaves inventory unchanged");check("12".equals(components(form()).stream().filter(c->c instanceof JTextField&&c instanceof JComponent j&&"Quantity".equals(j.getAccessibleContext().getAccessibleName())).map(c->((JTextField)c).getText()).findFirst().orElse("")),"cancel keeps form quantity");print("After cancel (unchanged)");edt(()->click(form(),"Cancel"));
        submit(false,"paracetamol",3,LocalDate.of(2050,12,13),"different expiry","Save");print("Different expiry (no prompt)");
        submit(false,"ibuprofen",2,expiry,"different name","Save");print("Different name (no prompt)");
        submit(false,"  PARACETAMOL ",1,expiry,"new dated remark","Merge quantities");print("Case and whitespace match with merged remarks");check(rows().stream().filter(m->m.getExpirationDate().equals(expiry)&&BatchMatcher.sameName(m.getName(),"paracetamol")).count()==2,"normalized name matched existing batch");check(rows().get(0).getRemarks().length()<=200,"merged remarks stay within 200 characters");
        int beforeCleanup=refreshCount();SwingUtilities.invokeLater(()->click(frame,"Merge duplicates"));waitFor(()->dialog("Merge duplicate batches")!=null);System.out.println("Duplicate cleanup confirmation: "+dialog("Merge duplicate batches").getTitle()+" (group details shown)");edt(()->click(dialog("Merge duplicate batches"),"Merge this group"));waitFor(()->rows().size()==3);waitFor(()->refreshCount()>beforeCleanup);print("After confirmed duplicate cleanup");
        Medicine expired=new Medicine("paracetamol",4,LocalDate.now().minusDays(1),LocalDate.now().minusDays(20),"old");check(BatchMatcher.find(List.of(expired)," PARACETAMOL ",expired.getExpirationDate()).isEmpty(),"expired batch does not match");
        app.login.logout();app.login.login("A001","assist123").orElseThrow();try{app.clinic().restockMedicine("paracetamol",1,expiry);throw new AssertionError("assistant restock unexpectedly allowed");}catch(SecurityException expected){System.out.println("PASS assistant role cannot restock");}
        check(audit.getAll().stream().filter(e->e.getAction().startsWith("restock ")).noneMatch(e->e.getDetails().contains("merge note")||e.getDetails().contains("separate note")||e.getDetails().contains("new dated remark")),"audit records contain no remarks");
        System.out.println("Inventory table reloads after committed choices: "+refresh+" -> "+refreshCount());
        edt(()->frame.dispose());app.close();System.out.println("DuplicateBatchDemo complete.");
    }
}
