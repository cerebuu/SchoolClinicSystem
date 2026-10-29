package clinic.app.ui.admin;

import clinic.app.ApplicationContext;
import clinic.model.Campus;
import clinic.model.User;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

/** Stored-catalog campus choices refresh when the admin saves a campus. */
public final class CampusChoiceFieldChecks {
    private static int checks;

    public static void main(String[] args)throws Exception {
        try(ApplicationContext app=new ApplicationContext(true)) {
            app.login.login("AD001","admin123").orElseThrow();
            CampusChoiceField[] assistantField=new CampusChoiceField[1];
            CampusChoiceField[] invalidField=new CampusChoiceField[1];
            SwingUtilities.invokeAndWait(()->{
                assistantField[0]=new CampusChoiceField(app,User.ASSISTANT,null);
                invalidField[0]=new CampusChoiceField(app,User.ASSISTANT,"removed-campus");
            });
            CampusChoiceField assistant=assistantField[0],invalid=invalidField[0];
            waitFor(()->assistant.isFormReady()==false&&assistant.comboBox().getItemCount()>0
                &&invalid.statusText().contains("Invalid campus assignment"));
            int total=app.admin.campuses(0,200,"").total();
            check(assistant.comboBox().getItemCount()==total,
                "assistant choices match the stored campus catalog");
            for(int i=0;i<assistant.comboBox().getItemCount();i++) {
                String label=assistant.comboBox().getItemAt(i);
                check(label.contains(" | ")&&app.admin.campuses(0,200,label.substring(0,label.indexOf(" | ")))
                    .rows().stream().anyMatch(campus->label.equals(campus.id()+" | "+campus.name())),
                    "assistant choice is a stored campus: "+label);
            }
            check(!assistant.isFormReady(),"assistant must choose a stored campus");
            check(invalid.comboBox().getItemCount()==total
                &&!invalid.statusText().isBlank(),"stale assignment is reported separately from choices");
            SwingUtilities.invokeAndWait(()->assistant.comboBox().setSelectedItem("C1 | Campus 1 (sample)"));
            check(assistant.isFormReady(),"stored assistant assignment is valid");

            app.admin.saveCampus(new Campus("NEW-CAMPUS","New campus"),false);
            waitFor(()->choiceIndex(assistant,"NEW-CAMPUS | New campus")>=0);
            check("C1".equals(assistant.formValue()),"new campus appears and current selection is preserved");
            app.admin.saveCampus(new Campus("NEW-CAMPUS","Renamed campus"),true);
            waitFor(()->choiceIndex(assistant,"NEW-CAMPUS | Renamed campus")>=0);
            check(choiceIndex(assistant,"NEW-CAMPUS | New campus")<0,
                "renamed campus replaces the old label");
            SwingUtilities.invokeAndWait(()->assistant.comboBox().setSelectedItem("NEW-CAMPUS | Renamed campus"));
            app.admin.deleteCampus("NEW-CAMPUS");
            waitFor(()->choiceIndex(assistant,"NEW-CAMPUS | Renamed campus")<0
                &&assistant.statusText().contains("Invalid campus assignment"));
            check(!assistant.isFormReady(),"removed campus is excluded and shown as an invalid assignment");
            assistant.close();invalid.close();
        }
        System.out.println("PASS: "+checks+" stored campus choice checks.");
    }

    private static int choiceIndex(CampusChoiceField field,String value) {
        for(int i=0;i<field.comboBox().getItemCount();i++)
            if(value.equals(field.comboBox().getItemAt(i)))return i;
        return -1;
    }

    private static void waitFor(java.util.function.BooleanSupplier ready)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<end) {
            AtomicBoolean result=new AtomicBoolean();
            SwingUtilities.invokeAndWait(()->result.set(ready.getAsBoolean()));
            if(result.get())return;
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for campus choices.");
    }

    private static void check(boolean passed,String label) {
        if(!passed)throw new AssertionError(label);
        checks++;
        System.out.println("PASS: "+label);
    }
}
