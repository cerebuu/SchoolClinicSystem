import clinic.app.*;
import clinic.app.ui.consultations.NewConsultationForm;
import clinic.app.ui.common.popup.MultiSelectField;
import clinic.model.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;

/** Automated visible demo using only synthetic in-memory data and the real secured form. */
public final class MultiSymptomDemo {
    private static JFrame frame;
    private static JDialog dialog;
    private static MultiSelectField picker;
    private static int checks;
    private static void edt(Runnable work)throws Exception {SwingUtilities.invokeAndWait(work);}
    private static List<Component> components(Container root) {
        List<Component> result=new ArrayList<>();
        for(Component c:root.getComponents()) {
            result.add(c);
            if(c instanceof Container container)result.addAll(components(container));
        }
        return result;
    }
    private static void check(String name,Object expected,Object actual) {
        System.out.println(name+" | expected="+expected+" | actual="+actual);
        if(!Objects.equals(expected,actual))throw new AssertionError(name);
        checks++;
    }
    private static JButton button(Container root,String text) {
        return components(root).stream().filter(c->c instanceof JButton b&&text.equals(b.getText()))
            .map(c->(JButton)c).findFirst().orElseThrow();
    }
    private static void clickChoice(String name) {
        components(picker).stream().filter(c->c instanceof JCheckBox b&&name.equals(b.getText()))
            .map(c->(JCheckBox)c).findFirst().orElseThrow().doClick();
    }
    private static void remove(String name) {
        components(picker).stream().filter(c->c instanceof JButton b&&("Remove "+name).equals(b.getAccessibleContext().getAccessibleName()))
            .map(c->(JButton)c).findFirst().orElseThrow().doClick();
    }
    private static void fill(String label,String value) {
        components(dialog).stream().filter(c->c instanceof JTextField t&&label.equals(t.getAccessibleContext().getAccessibleName()))
            .map(c->(JTextField)c).findFirst().orElseThrow().setText(value);
    }
    private static void await(java.util.function.BooleanSupplier ready)throws Exception {
        for(int i=0;i<150;i++) {
            AtomicBoolean done=new AtomicBoolean();
            edt(()->done.set(ready.getAsBoolean()));
            if(done.get())return;
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for popup");
    }
    public static void main(String[] args)throws Exception {
        ApplicationContext app=new ApplicationContext(true);
        try {
            app.login.login("N001","nurse123").orElseThrow();
            app.clinic().registerPatient(new Student("DEMO","Demo patient","demo","CS",1,"A"));
            edt(()->{
                Theme.install();
                frame=new JFrame("Multi-symptom consultation demo");
                frame.setSize(700,650); frame.setLocationRelativeTo(null); frame.setVisible(true);
            });
            SwingUtilities.invokeLater(()->NewConsultationForm.open(frame,app,()->{}));
            await(()->{
                for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isShowing()&&d.getTitle().equals("New Consultation")){dialog=d;return true;}
                return false;
            });
            edt(()->{
                picker=components(dialog).stream().filter(c->c instanceof MultiSelectField).map(c->(MultiSelectField)c).findFirst().orElseThrow();
                fill("Patient ID","DEMO");fill("Reason","Demo visit");
                clickChoice("Fever");clickChoice("Cough");clickChoice("Headache");
                check("Select three symptoms",List.of("Fever","Cough","Headache"),picker.getSelectedValues());
                remove("Cough");
                check("Remove Cough chip",List.of("Fever","Headache"),picker.getSelectedValues());
                fill("Search symptoms","head");
                check("Search head",List.of("Headache"),components(picker).stream().filter(c->c instanceof JCheckBox&&c.isVisible()).map(c->((JCheckBox)c).getText()).toList());
                check("Filtering keeps selections",List.of("Fever","Headache"),picker.getSelectedValues());
                remove("Fever");remove("Headache");button(dialog,"Save").doClick();
                check("Empty selection validation",true,components(dialog).stream().anyMatch(c->c instanceof JLabel l&&l.getText().equals("Select at least one symptom")));
                check("Empty save writes no visit",0,app.clinic().getVisitHistory("DEMO").size());
                fill("Search symptoms","");
                // Reverse click order still produces the stable controlled-list order.
                clickChoice("Headache");clickChoice("Fever");
                Dimension before=dialog.getSize();
                clickChoice("Cough");clickChoice("Sore throat");clickChoice("Abdominal pain");clickChoice("Other");
                check("All selections keep popup bounded",before,dialog.getSize());
                check("Cancel and Save visible",true,button(dialog,"Save").isShowing()&&button(dialog,"Cancel").isShowing());
                remove("Cough");remove("Sore throat");remove("Abdominal pain");remove("Other");
                check("Stable deduplicated save list",List.of("Fever","Headache"),picker.getSelectedValues());
                System.out.println("List passed to recordVisit: "+picker.getSelectedValues());
                button(dialog,"Save").doClick();
            });
            await(()->!dialog.isShowing());
            check("Stored through SecuredClinicSystem",List.of("Fever","Headache"),app.clinic().getVisitHistory("DEMO").get(0).getSymptoms());
            System.out.println("PASS: "+checks+" multi-symptom UI checks.");
        } finally {
            edt(()->{for(Window w:Window.getWindows())w.dispose();});
            app.close();
        }
    }
}
