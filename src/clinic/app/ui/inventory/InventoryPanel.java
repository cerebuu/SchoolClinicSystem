package clinic.app.ui.inventory;

import clinic.app.ApplicationContext;
import clinic.app.ui.common.*;
import clinic.app.ui.common.popup.*;
import clinic.model.*;
import clinic.service.BatchMatcher;
import clinic.service.PageResult;
import java.awt.BorderLayout;
import java.time.LocalDate;
import java.util.*;

public final class InventoryPanel extends BasePanel {
    private final ApplicationContext app;
    private final PagedTablePanel<Medicine> table;
    public InventoryPanel(ApplicationContext app) {
        super("Medicine inventory",app);this.app=app;
        table=new PagedTablePanel<>(new InventoryTableModel(),app::medicines);
        table.refreshOn(app.changes,clinic.app.DataTopic.INVENTORY);
        add(table,BorderLayout.CENTER);
        if(app.current().canAccess(User.RESTOCK)) {
            action("Add medicine",true,()->addBatch());
            action("Restock",()->restock());
            action("Merge duplicates",()->findDuplicates());
        }
        action("Stock and forecast",()->forecast());
    }
    private void addBatch() {
        FormPopup.showInteractive(this,"Add medicine batch",List.of(FormField.text("Medicine"),FormField.optional("Strength"),
            FormField.optional("Formulation"),FormField.positiveQuantity(),
            FormField.futureExpiry(),FormField.remarks()),Map.of(),(values,selected,controller)->
            checkBatch(values,product(values),false,controller),()->{});
    }
    private void restock() {
        Medicine medicine=table.selected();
        List<FormField> fields=medicine==null?List.of(FormField.text("Medicine"),FormField.optional("Strength"),
            FormField.optional("Formulation"),FormField.positiveQuantity(),
            FormField.futureExpiry(),FormField.remarks()):List.of(FormField.positiveQuantity(),
            FormField.futureExpiry(medicine.getExpirationDate().toString()),FormField.remarks(medicine.getRemarks()));
        FormPopup.showInteractive(this,"Restock"+(medicine==null?" medicine":" "+medicine.getDisplayName()),fields,Map.of(),
            (values,selected,controller)->checkRestock(values,medicine==null?product(values):medicine.getProduct(),medicine,controller),
            ()->MessagePopup.info(this,"Medicine restocked."));
    }
    private MedicineProduct product(Map<String,String> values) {
        return new MedicineProduct(values.get("Medicine"),values.get("Strength"),values.get("Formulation"));
    }
    private void checkRestock(Map<String,String> values,MedicineProduct product,Medicine selected,FormPopup.SaveController controller) {
        int quantity=Integer.parseInt(values.get("Quantity"));LocalDate expiry=LocalDate.parse(values.get("Expiry (YYYY-MM-DD)"));
        Actions.run(this,()->medicineBatches(product),batches->{
            Optional<Medicine> empty=BatchMatcher.depleted(batches,product,selected==null?null:selected.getBatchId());
            if(empty.isPresent()) {
                DuplicateBatchPopup.EmptyChoice choice=DuplicateBatchPopup.reuseEmpty(this,empty.get(),quantity,expiry,values.get("Remarks"));
                if(choice==DuplicateBatchPopup.EmptyChoice.CANCEL)return;
                boolean refill=choice==DuplicateBatchPopup.EmptyChoice.REUSE;
                controller.saveAsync(()->{
                    if(refill)app.refillBatchSession(empty.get().getBatchId(),product.displayName()).accept(values);
                    else app.inventoryBatchSession(product,true,false).accept(values);
                    return true;
                });
                return;
            }
            chooseMatchingBatch(values,product,expiry,quantity,true,controller,batches);
        });
    }
    private List<Medicine> medicineBatches(MedicineProduct product) {
        List<Medicine> batches=new ArrayList<>();int page=0,total;
        do {PageResult<Medicine> result=app.medicines(page++,100,product.name());batches.addAll(result.rows());total=result.total();}
        while(batches.size()<total);
        return batches.stream().filter(batch->batch.getProduct().key().equals(product.key())).toList();
    }
    private void checkBatch(Map<String,String> values,MedicineProduct product,boolean restock,FormPopup.SaveController controller) {
        int quantity=Integer.parseInt(values.get("Quantity"));LocalDate expiry=LocalDate.parse(values.get("Expiry (YYYY-MM-DD)"));
        Actions.run(this,()->app.matchingMedicineBatches(product,expiry),batches->{
            chooseMatchingBatch(values,product,expiry,quantity,restock,controller,batches);
        });
    }
    private void chooseMatchingBatch(Map<String,String> values,MedicineProduct product,LocalDate expiry,int quantity,
            boolean restock,FormPopup.SaveController controller,List<Medicine> batches) {
        Optional<BatchMatcher.Match> match=BatchMatcher.find(batches,product,expiry);
        boolean merge=false;
        if(match.isPresent()) {
            BatchMatcher.Match found=match.get();DuplicateBatchPopup.Choice choice;
            try { choice=DuplicateBatchPopup.show(this,found.batch(),found.count(),quantity,values.get("Remarks")); }
            catch(ArithmeticException overflow){MessagePopup.error(this,overflow);return;}
            if(choice==DuplicateBatchPopup.Choice.CANCEL)return;
            merge=choice==DuplicateBatchPopup.Choice.MERGE;
        }
        boolean mergeChoice=merge;
        controller.saveAsync(()->{app.inventoryBatchSession(product,restock,mergeChoice).accept(values);return true;});
    }
    private void findDuplicates() {
        Actions.run(this,()->{
            List<Medicine> batches=new ArrayList<>();int page=0,total;
            do {PageResult<Medicine> result=app.medicines(page++,100,"");batches.addAll(result.rows());total=result.total();}
            while(batches.size()<total);
            return BatchMatcher.duplicates(batches);
        },groups->{
            if(groups.isEmpty()){MessagePopup.info(this,"No unexpired duplicate batches were found.");return;}
            confirmDuplicateGroup(groups,0);
        });
    }
    private void confirmDuplicateGroup(List<BatchMatcher.Group> groups,int index) {
        if(index>=groups.size()){table.reload();return;}
        BatchMatcher.Group group=groups.get(index);
        StringBuilder details=new StringBuilder("Merge these ").append(group.batches().size()).append(" batches?\n")
            .append(group.product().displayName()).append(" — expiry ").append(group.expiry()).append('\n');
        group.batches().forEach(batch->details.append("Quantity ").append(batch.getQuantity()).append(", batch ")
            .append(batch.getBatchDate()).append(", remarks: ").append(batch.getRemarks().isBlank()?"(none)":batch.getRemarks()).append('\n'));
        if(!DuplicateBatchPopup.confirmGroup(this,details.toString())){confirmDuplicateGroup(groups,index+1);return;}
        Actions.run(this,()->{app.mergeDuplicateSession().accept(group);return true;},done->confirmDuplicateGroup(groups,index+1));
    }
    private void forecast() {
        Medicine medicine=table.selected();
        if(medicine==null){MessagePopup.warning(this,"Select a medicine.");return;}
        Actions.run(this,()->"Available unexpired stock: "+app.clinic().getStock(medicine.getProduct())+
            "\nEstimated days remaining: "+app.clinic().estimateDaysRemaining(medicine.getName())+
            " (-1 means no usage history)",message->MessagePopup.info(this,message));
    }
}
