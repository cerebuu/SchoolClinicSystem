import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.app.ui.common.MedicinePicker;
import clinic.app.ui.dispense.DispenseItemsPanel;
import clinic.app.ui.inventory.InventoryProductRow;
import clinic.app.ui.inventory.InventoryTableModel;
import clinic.core.ClinicSystem;
import clinic.model.MedicalRecord;
import clinic.model.Medicine;
import clinic.model.MedicineProduct;
import clinic.model.Student;
import clinic.service.AuditLog;
import clinic.service.ConsultationWorkflow;
import clinic.service.DispenseItem;
import java.awt.Component;
import java.awt.Container;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Headless component, consultation, report, allergy, and depletion regressions. */
public final class ConsultationEnhancementChecks {
    private static int checks;
    private static void check(boolean ok,String label) {
        if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS: "+label);
    }
    private static void waitFor(java.util.function.BooleanSupplier ready)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
        while(System.nanoTime()<end) {
            AtomicBoolean done=new AtomicBoolean();SwingUtilities.invokeAndWait(()->done.set(ready.getAsBoolean()));
            if(done.get())return;Thread.sleep(15);
        }
        throw new AssertionError("Timed out waiting for Swing result.");
    }
    private static List<Component> descendants(Container root) {
        List<Component> result=new java.util.ArrayList<>();
        for(Component child:root.getComponents()){result.add(child);if(child instanceof Container c)result.addAll(descendants(c));}
        return result;
    }
    @SuppressWarnings("unchecked") private static JComboBox<Object> selector(Container root) {
        return (JComboBox<Object>)descendants(root).stream().filter(JComboBox.class::isInstance)
            .map(JComboBox.class::cast).findFirst().orElseThrow();
    }
    private static JLabel label(Container root,String name) {
        return descendants(root).stream().filter(c->c instanceof JLabel l&&name.equals(l.getName()))
            .map(JLabel.class::cast).findFirst().orElseThrow();
    }
    private static void choose(JComboBox<?> box,String name) {
        for(int i=0;i<box.getItemCount();i++)if(box.getItemAt(i) instanceof Medicine m&&m.getName().equals(name)) {
            box.setSelectedItem(m);return;
        }
        throw new AssertionError("Medicine result is missing: "+name);
    }
    private static void search(JComboBox<?> box,String value) {
        ((javax.swing.JTextField)box.getEditor().getEditorComponent()).setText(value);
    }

    public static void main(String[] args)throws Exception {
        Theme.install();AuditLog audit=new AuditLog();
        try(ApplicationContext app=new ApplicationContext(true,audit)) {
            app.login.login("N001","nurse123").orElseThrow();ClinicSystem clinic=app.clinic();
            clinic.registerPatient(new Student("qa-clean","Synthetic record","fixture","CS",1,"A"));
            clinic.registerPatient(new Student("qa-allergy","Synthetic allergy record","fixture","CS",1,"A"));
            clinic.addAllergy("qa-allergy","Penicillin");
            MedicineProduct spent=new MedicineProduct("Spent medicine","A01","A01B");
            MedicineProduct amoxicillin=new MedicineProduct("Amoxicillin 500mg","J01CA04","");
            clinic.addMedicine(spent,1,LocalDate.now().plusDays(60),"");
            clinic.addMedicine(amoxicillin,4,LocalDate.now().plusDays(90),"");
            clinic.dispenseMedicine("qa-clean",spent,1,"N001",false);
            var zero=app.dispensableMedicines(0,25,"Spent").rows().get(0);
            AtomicBoolean selectable=new AtomicBoolean(true);
            Medicine amoxChoice=app.dispensableMedicines(0,25,"Amoxicillin").rows().get(0);
            JPanel root=new JPanel();DispenseItemsPanel panel=new DispenseItemsPanel(app,true);
            SwingUtilities.invokeAndWait(()->{panel.setPatient(clinic.findPatient("qa-clean").orElseThrow());root.add(panel);});
            JComboBox<Object> box=selector(panel);
            SwingUtilities.invokeAndWait(()->{
                box.addItem(zero);box.addItem(amoxChoice);
                @SuppressWarnings("unchecked") var renderer=(javax.swing.ListCellRenderer<Object>)box.getRenderer();
                Component cell=renderer.getListCellRendererComponent(new javax.swing.JList<>(),zero,0,false,false);
                check(((JLabel)cell).getText().contains("Out of stock"),"zero-usable-stock result is labeled Out of stock");
                choose(box,"Spent medicine");selectable.set(((MedicinePicker)panelSelector(panel)).selected()!=null);
            });
            check(!selectable.get(),"out-of-stock result cannot be selected");
            SwingUtilities.invokeAndWait(()->choose(box,"Amoxicillin 500mg"));
            waitFor(()->label(panel,"medicine-safety").getText().contains("No allergy conflict found"));
            check(true,"patient with no recorded allergy gets an asynchronous safety result");
            SwingUtilities.invokeAndWait(()->panel.setPatient(clinic.findPatient("qa-allergy").orElseThrow()));
            waitFor(()->label(panel,"medicine-safety").getText().contains("Allergy warning"));
            AtomicBoolean allergyVisible=new AtomicBoolean();
            SwingUtilities.invokeAndWait(()->allergyVisible.set(descendants(panel).stream()
                .filter(c->c instanceof javax.swing.JTextArea area&&"recorded-allergy-notice".equals(area.getName()))
                .map(c->((javax.swing.JTextArea)c).getText()).anyMatch(text->text.contains("Penicillin"))));
            check(allergyVisible.get(),"recorded allergy appears once above medicine rows");
            SwingUtilities.invokeAndWait(()->panel.removeNotify());
            InventoryTableModel inventory=new InventoryTableModel();
            inventory.replace(new clinic.service.PageResult<InventoryProductRow>(
                List.of(new InventoryProductRow(spent,0,null,List.of())),1));
            check("Out of stock".equals(inventory.getValueAt(0,2)),"Inventory shows Out of stock for zero usable units");

            MedicineProduct batches=new MedicineProduct("Two-batch medicine","B01","");
            LocalDate early=LocalDate.now().plusDays(5),late=LocalDate.now().plusDays(30);
            clinic.addMedicine(batches,2,early,"");clinic.addMedicine(batches,3,late,"");
            List<Medicine> ids=app.medicines(0,100,"Two-batch medicine").rows();
            clinic.dispenseMedicine("qa-clean",batches,1,"N001",false);printStock(app,batches,"part first batch");
            var crossing=clinic.dispenseMedicine("qa-clean",batches,2,"N001",false);
            check(crossing.getBatchAllocations().size()==2&&crossing.getBatchAllocations().get(0).batchId().equals(ids.get(0).getBatchId()),
                "cross-batch dispense consumes earliest expiry first");
            check(app.medicines(0,100,"Two-batch medicine").rows().get(0).getQuantity()==0,
                "cross-batch dispense empties the earliest batch before using the next");
            printStock(app,batches,"crossed both batches; first batch emptied");
            clinic.dispenseMedicine("qa-clean",batches,2,"N001",false);printStock(app,batches,"all batches depleted");
            int before=app.transactions(0,100,"Two-batch medicine",null,null).total();
            try{clinic.dispenseMedicine("qa-clean",batches,1,"N001",false);throw new AssertionError("Expected empty stock rejection.");}
            catch(IllegalStateException expected){check(expected.getMessage().contains("out of stock"),"one more dispense is clearly rejected at zero stock");}
            check(app.transactions(0,100,"Two-batch medicine",null,null).total()==before,"failed final dispense records no transaction");
            check(clinic.getLowStock().stream().anyMatch(m->m.getProduct().key().equals(batches.key())),
                "depleted medicine appears in low-stock alerts");

            String override="Reviewed recorded allergies and verified this order.";
            clinic.addMedicine(amoxicillin,2,LocalDate.now().plusDays(90),"");
            MedicalRecord allergyVisit=((ConsultationWorkflow)clinic).recordVisitAndAdministerWithReason("qa-allergy",
                List.of("Cough"),"Review","","N001",List.of(new DispenseItem(amoxicillin,1)),override);
            var savedOverride=app.transactions(0,100,"Amoxicillin",null,null).rows().stream()
                .filter(tx->allergyVisit.getRecordId().equals(tx.getVisitId())).findFirst().orElseThrow();
            check(savedOverride.getRemarks().contains(override),"confirmed override reason is retained on the dispensing record");
            check(audit.getAll().stream().noneMatch(e->e.getDetails().contains(override)||e.getDetails().contains("Penicillin")),
                "audit entries contain no override reason or allergy text");

            MedicineProduct a=new MedicineProduct("Consultation medicine A","A01","");
            MedicineProduct b=new MedicineProduct("Consultation medicine B","B01","");
            clinic.addMedicine(a,3,LocalDate.now().plusDays(90),"");clinic.addMedicine(b,4,LocalDate.now().plusDays(90),"");
            MedicalRecord visit=((ConsultationWorkflow)clinic).recordVisitAndAdministerWithReason("qa-clean",
                List.of("Fever"),"Two medicines recorded","","N001",
                List.of(new DispenseItem(a,1),new DispenseItem(b,2)),"");
            String report=clinic.generateReport(visit);
            check(report.contains("Consultation medicine A x 1")&&report.contains("Consultation medicine B x 2"),
                "generated report contains both administered medicines");
            System.out.println("REPORT_TEXT_BEGIN\n"+report+"\nREPORT_TEXT_END");
            check(app.records(0,100,"").rows().stream().anyMatch(r->r.getRecordId().equals(visit.getRecordId())),"saved consultation appears in Consultations and Reports");
            check(app.recentVisits(0,100,"").rows().stream().anyMatch(r->r.getRecordId().equals(visit.getRecordId())),"saved consultation appears in Recent visits");
            check(app.medicationHistory(0,100,"",null,null).rows().stream().filter(e->visit.getRecordId().equals(e.visitId())).count()==2,
                "each consultation dispense appears in Medication history");
            System.out.println("PASS: "+checks+" consultation enhancement checks.");
        }
    }

    private static boolean contains(JComboBox<?> box,String name) {
        return java.util.stream.IntStream.range(0,box.getItemCount())
            .anyMatch(i->box.getItemAt(i) instanceof Medicine m&&m.getName().equals(name));
    }
    private static Object panelSelector(DispenseItemsPanel panel) {
        return descendants(panel).stream().filter(MedicinePicker.class::isInstance).findFirst().orElseThrow();
    }
    private static void printStock(ApplicationContext app,MedicineProduct product,String step) {
        var batches=app.medicines(0,100,product.name()).rows();
        System.out.println("STOCK_STEP "+step+" | batches="+batches.stream()
            .map(m->m.getQuantity()+"@"+m.getExpirationDate()).toList()+" | total="+app.clinic().getStock(product));
    }
}
