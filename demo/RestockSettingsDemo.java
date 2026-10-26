import clinic.app.*;
import clinic.app.nav.SidebarItem;
import clinic.app.ui.inventory.InventoryPanel;
import clinic.model.Medicine;
import clinic.service.AuditLog;
import java.awt.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import javax.swing.text.JTextComponent;

/** Visible automated demo of the actual inventory popup and role navigators; synthetic data only. */
public final class RestockSettingsDemo {
    private static JFrame frame;
    private static JDialog dialog;
    private static InventoryPanel inventory;
    private static int checks;
    private static void edt(Runnable work) throws Exception { SwingUtilities.invokeAndWait(work); }
    private static List<Component> all(Container root) {
        List<Component> result = new ArrayList<>();
        for (Component c : root.getComponents()) {
            result.add(c);
            if(c instanceof Container nested) result.addAll(all(nested));
        }
        return result;
    }
    private static JButton button(Container root, String title) {
        return all(root).stream().filter(c -> c instanceof JButton b && title.equals(b.getText()))
            .map(c -> (JButton)c).findFirst().orElseThrow();
    }
    private static void fill(String label, String value) {
        all(dialog).stream().filter(c -> c instanceof JTextComponent t
            && label.equals(t.getAccessibleContext().getAccessibleName()))
            .map(c -> (JTextComponent)c).findFirst().orElseThrow().setText(value);
    }
    private static void check(String label, boolean passed) {
        System.out.println((passed ? "PASS: " : "FAIL: ") + label);
        if(!passed) throw new AssertionError(label);
        checks++;
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        for(int i=0;i<150;i++) {
            AtomicBoolean ready = new AtomicBoolean();
            edt(() -> ready.set(condition.getAsBoolean()));
            if(ready.get()) return;
            Thread.sleep(100);
        }
        throw new AssertionError("UI wait timed out");
    }
    private static void open() throws Exception {
        await(() -> all(inventory).stream().anyMatch(c -> c instanceof JTable t && t.getRowCount()>0));
        edt(() -> all(inventory).stream().filter(c -> c instanceof JTable).map(c -> (JTable)c)
            .findFirst().orElseThrow().setRowSelectionInterval(0,0));
        SwingUtilities.invokeLater(() -> button(inventory,"Restock").doClick());
        await(() -> {
            for(Window w:Window.getWindows()) if(w instanceof JDialog d && d.isShowing()
                    && d.getTitle().equals("Restock Demo medicine")) { dialog=d; return true; }
            return false;
        });
        edt(() -> { fill("Quantity","2"); fill("Expiry (YYYY-MM-DD)",LocalDate.now().plusDays(30).toString()); });
    }
    private static void printBatches(ApplicationContext app, String label) {
        System.out.println(label + " stored batches:");
        for(Medicine m:app.medicines(0,25,"").rows()) System.out.println("  name="+m.getName()
            +", quantity="+m.getQuantity()+", expiry="+m.getExpirationDate()+", batchDate="+m.getBatchDate()
            +", remarks=\""+m.getRemarks()+"\"");
    }
    private static void save(ApplicationContext app,String remarks,String expected) throws Exception {
        open();
        int count=app.medicines(0,25,"").total();
        edt(() -> {
            fill("Remarks",remarks);
            check("Counter updates to " + Math.min(remarks.length(),200),all(dialog).stream()
                .anyMatch(c -> c instanceof JLabel l && l.getText().equals(Math.min(remarks.length(),200)+" / 200")));
            check("Cancel and Save visible",button(dialog,"Save").isShowing() && button(dialog,"Cancel").isShowing());
            button(dialog,"Save").doClick();
        });
        await(() -> !dialog.isShowing());
        List<Medicine> batches=app.medicines(0,25,"").rows();
        check("One new batch with exact normalized remarks",batches.size()==count+1
            && batches.get(batches.size()-1).getRemarks().equals(expected));
        printBatches(app,"After remarks length " + remarks.length());
    }
    private static void invalid(ApplicationContext app,String label,String value,String error) throws Exception {
        open();
        int before=app.medicines(0,25,"").total();
        edt(() -> {
            fill(label,value); button(dialog,"Save").doClick();
            check(label+"="+value+" inline error: "+error,all(dialog).stream()
                .anyMatch(c -> c instanceof JLabel l && error.equals(l.getText())));
            button(dialog,"Cancel").doClick();
        });
        check("Invalid input creates no batch",app.medicines(0,25,"").total()==before);
        printBatches(app,"After rejected "+label+"="+value);
    }
    public static void main(String[] args) throws Exception {
        AuditLog audit=new AuditLog();
        try(ApplicationContext app=new ApplicationContext(true,audit)) {
            app.login.login("N001","nurse123").orElseThrow();
            app.clinic().addMedicine("Demo medicine",1,LocalDate.now().plusDays(60),"");
            edt(() -> {
                Theme.install(); frame=new JFrame("Restock and navigation demo");
                frame.setSize(1200,850); inventory=new InventoryPanel(app);
                frame.setContentPane(inventory); frame.setVisible(true);
            });
            save(app,"  Supplier delivery checked  ","Supplier delivery checked");
            save(app,"   ","");
            save(app,"x".repeat(201),"x".repeat(200));
            invalid(app,"Quantity","1.5","Quantity must be a whole number of at least 1.");
            invalid(app,"Quantity","0","Quantity must be a whole number of at least 1.");
            invalid(app,"Expiry (YYYY-MM-DD)",LocalDate.now().minusDays(1).toString(),
                "Expiry must be a valid future date (YYYY-MM-DD).");
            invalid(app,"Expiry (YYYY-MM-DD)","2026-02-30","Expiry must be a valid future date (YYYY-MM-DD).");
            check("Audit contains medicine only, never remarks",audit.getAll().stream()
                .filter(e -> e.getAction().equals("restockMedicine"))
                .allMatch(e -> e.getDetails().equals("Demo medicine") && e.getUserId().equals("N001")));
            edt(() -> {
                JTable table=all(inventory).stream().filter(c -> c instanceof JTable).map(c -> (JTable)c).findFirst().orElseThrow();
                Object value=table.getValueAt(1,4);
                JComponent cell=(JComponent)table.prepareRenderer(table.getCellRenderer(1,4),1,4);
                check("Remarks column has full text tooltip",table.getColumnName(4).equals("Remarks")
                    && value.toString().equals(cell.getToolTipText()));
            });
            for(String[] role:List.of(new String[]{"N001","nurse123","Nurse"},
                    new String[]{"A001","assist123","NurseAssistant"},new String[]{"D001","doctor123","Doctor"},
                    new String[]{"AD001","admin123","Administrator"})) {
                app.login.logout(); app.login.login(role[0],role[1]).orElseThrow();
                edt(() -> {
                    Router router=new Router(frame,app); router.workspace();
                    List<String> items=all(frame).stream().filter(c -> c instanceof SidebarItem)
                        .map(c -> ((SidebarItem)c).getText()).toList();
                    System.out.println(role[2]+" navigator: "+items+"; Sign out");
                    check(role[2]+" no Settings tab",items.stream().noneMatch(s -> s.toLowerCase().contains("settings")));
                    JButton logout=button(frame,"Sign out"); check(role[2]+" logout visible",logout.isShowing());
                    logout.doClick();
                    check(role[2]+" logout returns to login",app.login.getCurrentUser().isEmpty()
                        && all(frame).stream().anyMatch(c -> c instanceof JButton b && b.getText().equals("Sign in")));
                });
            }
            System.out.println("PASS: "+checks+" restock/settings UI checks.");
        } finally { edt(() -> { for(Window w:Window.getWindows())w.dispose(); }); }
    }
}
