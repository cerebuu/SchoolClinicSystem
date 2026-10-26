import clinic.app.*;
import clinic.app.ui.dispense.*;
import clinic.app.ui.consultations.*;
import clinic.app.ui.common.popup.MessagePopup;
import clinic.model.*;
import clinic.service.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;

/** Exercises the actual multi-row dispense panel and modal confirmation with synthetic data. */
public final class AllergyConfirmationDemo {
    private static JFrame frame;
    private static JDialog dialog;
    private static int checks;
    private static void edt(Runnable work)throws Exception { SwingUtilities.invokeAndWait(work); }
    private static List<Component> all(Container root) {
        List<Component> result=new ArrayList<>();
        for(Component c:root.getComponents()){result.add(c);if(c instanceof Container nested)result.addAll(all(nested));}
        return result;
    }
    private static JButton button(Container root,String label) {
        return all(root).stream().filter(c->c instanceof JButton b&&label.equals(b.getText())).map(c->(JButton)c).findFirst().orElseThrow();
    }
    private static void check(String message,boolean ok) {
        if(!ok)throw new AssertionError(message);
        System.out.println("PASS: "+message);checks++;
    }
    private static void await(java.util.function.BooleanSupplier condition)throws Exception {
        for(int i=0;i<150;i++) {
            AtomicBoolean ready=new AtomicBoolean();edt(()->ready.set(condition.getAsBoolean()));
            if(ready.get())return;Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for UI");
    }
    private static void awaitDialog(String title)throws Exception {
        await(()->{for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&title.equals(d.getTitle())){dialog=d;return true;}return false;});
    }
    private static List<JComboBox<?>> medicines() {
        return all(frame).stream().filter(c->c instanceof JComboBox<?> b&&"Medicine".equals(b.getAccessibleContext().getAccessibleName()))
            .<JComboBox<?>>map(c->(JComboBox<?>)c).toList();
    }
    private static void prepare(ApplicationContext app,String id)throws Exception {
        edt(()->{
            frame.setContentPane(new DispensePanel(app));frame.revalidate();
            all(frame).stream().filter(c->c instanceof JTextField t&&"Patient ID".equals(t.getName()))
                .map(c->(JTextField)c).findFirst().orElseThrow().setText(id);
            button(frame,"Find patient").doClick();button(frame,"+ Add medicine").doClick();
        });
        await(()->medicines().size()==2&&medicines().stream().allMatch(c->c.isEnabled()&&c.getItemCount()>=2)
            &&all(frame).stream().anyMatch(c->c instanceof JLabel l&&l.getText().startsWith("Confirmed: ")));
        edt(()->{
            List<String> names=List.of("paracetamol","amoxicillin");
            for(int row=0;row<2;row++) {
                JComboBox<?> choices=medicines().get(row);
                for(int i=0;i<choices.getItemCount();i++)if(((Medicine)choices.getItemAt(i)).getName().equals(names.get(row))) {
                    choices.setSelectedIndex(i);break;
                }
            }
        });
    }
    private static void submit()throws Exception {edt(()->button(frame,"Dispense").doClick());}
    public static void main(String[] args)throws Exception {
        AuditLog audit=new AuditLog();
        try(ApplicationContext app=new ApplicationContext(true,audit)) {
            app.login.login("N001","nurse123").orElseThrow();
            Patient p=new Student("allergic","PRIVATE PATIENT NAME","contact","CS",1,"A");
            p.addAllergy("Penicillin");p.addAllergy("Aspirin");app.clinic().registerPatient(p);
            app.clinic().registerPatient(new Student("none","OTHER PRIVATE NAME","contact","CS",1,"A"));
            for(String name:List.of("paracetamol","amoxicillin"))app.clinic().addMedicine(name,10,LocalDate.now().plusDays(30),"");
            System.out.println("checkSafety(paracetamol): "+app.clinic().checkSafety("allergic","paracetamol"));
            check("Only generic reason, no allergy text",app.clinic().checkSafety("allergic","paracetamol").equals(List.of(SafetyChecker.ALLERGY_CONFIRMATION)));
            try{app.clinic().dispenseMedicine("allergic","paracetamol",1,"N001",false);throw new AssertionError("Missing confirmation allowed");}
            catch(IllegalStateException expected){System.out.println("Unconfirmed dispense blocked: "+expected.getMessage());}
            edt(()->{Theme.install();frame=new JFrame("Allergy confirmation demo");frame.setSize(1200,900);frame.setVisible(true);});
            prepare(app,"allergic");submit();awaitDialog("Confirm allergies before dispensing");
            edt(()->{
                check("Exactly one confirmation covers all rows",Arrays.stream(Window.getWindows()).filter(w->w instanceof JDialog&&w.isShowing()).count()==1);
                String text=all(dialog).stream().filter(c->c instanceof JTextArea).map(c->((JTextArea)c).getText()).findFirst().orElseThrow();
                System.out.println("Popup contents:\n"+text);
                check("Both allergies and both medicine quantities shown",text.contains("Penicillin\nAspirin")
                    &&text.contains("paracetamol — quantity 1")&&text.contains("amoxicillin — quantity 1"));
                check("Cancel is default",dialog.getRootPane().getDefaultButton()==button(dialog,"Cancel"));
                check("Dispense disabled before checkbox",!button(dialog,"Dispense").isEnabled());button(dialog,"Cancel").doClick();
            });
            check("Cancel dispenses nothing",app.transactions(0,25,null,null,null).total()==0&&app.clinic().getStock("paracetamol")==10);
            submit();awaitDialog("Confirm allergies before dispensing");
            edt(()->{
                all(dialog).stream().filter(c->c instanceof JCheckBox).map(c->(JCheckBox)c).findFirst().orElseThrow().doClick();
                check("Checkbox enables Dispense",button(dialog,"Dispense").isEnabled());button(dialog,"Dispense").doClick();
            });
            awaitDialog("School clinic");edt(()->dialog.dispose());
            check("Both rows dispensed after confirmation",app.transactions(0,25,null,null,null).total()==2);
            prepare(app,"none");submit();awaitDialog("School clinic");
            edt(()->{check("No-allergy patient skips confirmation",Arrays.stream(Window.getWindows()).noneMatch(w->w instanceof JDialog d
                &&d.isShowing()&&d.getTitle().equals("Confirm allergies before dispensing")));dialog.dispose();});
            check("No-allergy patient dispensed directly",app.transactions(0,25,null,null,null).total()==4);
            // Stock changes after selection must still block before a confirmation opens.
            prepare(app,"allergic");app.clinic().dispenseMedicine("none","paracetamol",8,"N001",false);
            submit();awaitDialog("Unable to complete");
            edt(()->{check("Out-of-stock blocked by MessagePopup",all(dialog).stream().anyMatch(c->c instanceof JLabel l
                &&l.getText()!=null&&l.getText().contains("out of stock")));dialog.dispose();});
            check("Failed preflight leaves second row untouched",app.clinic().getStock("amoxicillin")==8);
            for(var entry:audit.getAll()) {
                checkPrivate(entry);
                if(entry.getAction().equals("dispense with allergy confirmation"))System.out.println("Audit: user="+entry.getUserId()
                    +", action="+entry.getAction()+", details="+entry.getDetails());
            }
            check("Two confirmed rows audited with count only",audit.getAll().stream().filter(e->e.getAction().equals("dispense with allergy confirmation")
                &&e.getDetails().equals("Allergy count: 2")).count()==2);
            hardChecks(p);
            SwingUtilities.invokeLater(()->NewConsultationForm.open(frame,app,()->{}));awaitDialog("New Consultation");
            edt(()->{
                check("Consultation popup uses Concern",all(dialog).stream().anyMatch(c->c instanceof JTextField t
                    &&"Concern".equals(t.getAccessibleContext().getAccessibleName())));
                check("Consultation popup has no Reason label",all(dialog).stream().noneMatch(c->c instanceof JLabel l&&l.getText().contains("Reason")));
                button(dialog,"Cancel").doClick();
            });
            check("Consultations table uses Concern",new ConsultationsTableModel().getColumnName(3).equals("Concern"));
            String report=new MedicalRecord("visit","allergic",List.of("Fever"),"Demo concern","","","N001").toReport();
            System.out.println("Consultation report:\n"+report);
            check("Report uses Concern, not Reason",report.contains("Concern: Demo concern")&&!report.contains("Reason"));
            System.out.println("PASS: "+checks+" confirmation and consultation checks.");
        } finally {edt(()->{for(Window w:Window.getWindows())w.dispose();});}
    }
    private static void checkPrivate(AuditEntry e) {
        if(e.getDetails().contains("Penicillin")||e.getDetails().contains("Aspirin")||e.getDetails().contains("PRIVATE"))throw new AssertionError("Audit privacy");
    }
    private static void hardChecks(Patient p)throws Exception {
        InMemoryClinicSystem db=new InMemoryClinicSystem(new InMemoryClinicSystem.State(List.of(p),List.of(),List.of(),List.of(),List.of(
            new Medicine("expired",10,LocalDate.now().minusDays(1),LocalDate.now().minusDays(30),""),
            new Medicine("zero",0,LocalDate.now().plusDays(1),LocalDate.now(),""))));
        for(String medicine:List.of("expired","zero","unknown")) {
            try{db.dispenseMedicine(p.getId(),medicine,1,"n",true);throw new AssertionError("Hard error overridden");}
            catch(IllegalStateException expected){System.out.println("Hard block with confirmation ("+medicine+"): "+expected.getMessage());}
        }
        check("Hard failures create no transactions",db.snapshot().transactions().isEmpty());
        List<String> expired=db.checkSafety(p.getId(),"expired");
        SwingUtilities.invokeLater(()->MessagePopup.error(frame,new IllegalStateException(String.join("\n",expired))));
        awaitDialog("Unable to complete");edt(()->dialog.dispose());
    }
}
