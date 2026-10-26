import clinic.app.*;
import clinic.app.ui.patients.PatientsPanel;
import clinic.app.ui.common.popup.AllergyField;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;

/** Exercises the real registration and Add allergy popups, including debounce and validation. */
public final class AllergyUiDemo {
    private static JFrame frame;
    private static JDialog dialog;
    private static int checks;
    private static void edt(Runnable action)throws Exception { SwingUtilities.invokeAndWait(action); }
    private static List<Component> all(Container root) {
        List<Component> result=new ArrayList<>();
        for(Component c:root.getComponents()) { result.add(c); if(c instanceof Container nested)result.addAll(all(nested)); }
        return result;
    }
    private static JButton button(Container root,String text) {
        return all(root).stream().filter(c->c instanceof JButton b&&text.equals(b.getText())).map(c->(JButton)c).findFirst().orElseThrow();
    }
    private static void fill(String label,String value) {
        all(dialog).stream().filter(c->c instanceof JTextField t&&label.equals(t.getAccessibleContext().getAccessibleName()))
            .map(c->(JTextField)c).findFirst().orElseThrow().setText(value);
    }
    private static void check(String message,boolean condition) {
        if(!condition)throw new AssertionError(message);
        System.out.println("PASS: "+message); checks++;
    }
    private static void await(java.util.function.BooleanSupplier condition)throws Exception {
        for(int i=0;i<150;i++) {
            AtomicBoolean ready=new AtomicBoolean(); edt(()->ready.set(condition.getAsBoolean()));
            if(ready.get())return; Thread.sleep(100);
        }
        throw new AssertionError("UI timed out");
    }
    private static List<JButton> chips() {
        return all(dialog).stream().filter(c->c instanceof JButton b&&b.getAccessibleContext().getAccessibleName()!=null
            &&b.getAccessibleContext().getAccessibleName().startsWith("Remove allergy ")).map(c->(JButton)c).toList();
    }
    private static void open(String title)throws Exception {
        SwingUtilities.invokeLater(()->button(frame,title).doClick());
        await(()->{
            for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&title.equals(d.getTitle())) {dialog=d;return true;}
            return false;
        });
    }
    public static void main(String[] args)throws Exception {
        try(ApplicationContext app=new ApplicationContext(true)) {
            app.login.login("N001","nurse123").orElseThrow();
            edt(()->{Theme.install();frame=new JFrame("Allergy registration demo");frame.setSize(1100,850);
                frame.setContentPane(new PatientsPanel(app));frame.setVisible(true);});
            open("Register patient");
            edt(()->{
                fill("ID","demo");fill("Name","Synthetic patient");fill("Contact","contact");
                fill("Course / Department","CS");fill("Section / Position","A");
                fill("Allergies","Penicillin, penicillin, Aspirin");
            });
            await(()->chips().size()==2);
            edt(()->{
                check("Debounced preview deduplicates",chips().size()==2);
                check("Chips use neutral colors",chips().stream().allMatch(b->b.getBackground().equals(Theme.SUBTLE)));
                check("Separator hint shown",all(dialog).stream().anyMatch(c->c instanceof JLabel l&&l.getText().equals("Separate with commas")));
                chips().stream().filter(b->b.getAccessibleContext().getAccessibleName().equals("Remove allergy Penicillin"))
                    .findFirst().orElseThrow().doClick();
                check("Removing chip updates input",all(dialog).stream().anyMatch(c->c instanceof JTextField t
                    &&"Allergies".equals(t.getAccessibleContext().getAccessibleName())&&t.getText().equals("Aspirin")));
                fill("Allergies","x".repeat(51));button(dialog,"Save").doClick();
                check("Immediate save blocks 51 characters inline",all(dialog).stream().anyMatch(c->c instanceof JLabel l
                    &&l.getText().equals("Each allergy must be at most 50 characters.")));
                check("Invalid save registers no patient",app.clinic().findPatient("demo").isEmpty());
                fill("Allergies",String.join(",",java.util.stream.IntStream.range(0,21).mapToObj(i->"A"+i).toList()));
                button(dialog,"Save").doClick();
                check("Immediate save blocks 21 items inline",all(dialog).stream().anyMatch(c->c instanceof JLabel l
                    &&l.getText().equals("Enter at most 20 allergies.")));
                check("Save and Cancel remain visible",button(dialog,"Save").isShowing()&&button(dialog,"Cancel").isShowing());
                fill("Allergies","Penicillin\nAspirin");button(dialog,"Save").doClick();
            });
            await(()->!dialog.isShowing());
            check("Popup saves separate allergies including pasted newline",app.clinic().findPatient("demo").orElseThrow()
                .getAllergies().equals(List.of("Penicillin","Aspirin")));
            await(()->all(frame).stream().anyMatch(c->c instanceof JTable t&&t.getRowCount()>0));
            edt(()->all(frame).stream().filter(c->c instanceof JTable).map(c->(JTable)c).findFirst().orElseThrow().setRowSelectionInterval(0,0));
            open("Add allergy");
            edt(()->{
                check("Add allergy reuses same component",all(dialog).stream().anyMatch(c->c instanceof AllergyField));
                fill("Allergies","Peanuts;Latex;PEANUTS");button(dialog,"Save").doClick();
            });
            await(()->app.clinic().findPatient("demo").orElseThrow().getAllergies().size()==4);
            System.out.println("After Add allergy: "+app.clinic().findPatient("demo").orElseThrow().getAllergies());
            check("Add allergy parses and deduplicates",app.clinic().findPatient("demo").orElseThrow().getAllergies()
                .equals(List.of("Penicillin","Aspirin","Peanuts","Latex")));
            // Allow the asynchronous success message to finish before disposing its window.
            await(()->!dialog.isShowing());
            System.out.println("PASS: "+checks+" allergy popup checks.");
        } finally { edt(()->{for(Window w:Window.getWindows())w.dispose();}); }
    }
}
