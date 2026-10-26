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
        super("Medicine inventory","Clinic records");this.app=app;
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
        FormPopup.showInteractive(this,"Add medicine batch",List.of(FormField.text("Medicine"),FormField.positiveQuantity(),
            FormField.futureExpiry(),FormField.remarks()),Map.of(),(values,selected,controller)->
            checkBatch(values,values.get("Medicine"),false,controller),()->{});
    }
    private void restock() {
        Medicine medicine=table.selected();
        List<FormField> fields=medicine==null?List.of(FormField.text("Medicine"),FormField.positiveQuantity(),
            FormField.futureExpiry(),FormField.remarks()):List.of(FormField.positiveQuantity(),
            FormField.futureExpiry(medicine.getExpirationDate().toString()),FormField.remarks(medicine.getRemarks()));
        FormPopup.showInteractive(this,"Restock"+(medicine==null?" medicine":" "+medicine.getName()),fields,Map.of(),
            (values,selected,controller)->checkRestock(values,medicine,controller),()->MessagePopup.info(this,"Medicine restocked."));
    }
    private void checkRestock(Map<String,String> values,Medicine selected,FormPopup.SaveController controller) {
        String name=selected==null?values.get("Medicine"):selected.getName();
        int quantity=Integer.parseInt(values.get("Quantity"));LocalDate expiry=LocalDate.parse(values.get("Expiry (YYYY-MM-DD)"));
        Actions.run(this,()->medicineBatches(name),batches->{
            Optional<Medicine> empty=BatchMatcher.depleted(batches,name,selected==null?null:selected.getBatchId());
            if(empty.isPresent()) {
                DuplicateBatchPopup.EmptyChoice choice=DuplicateBatchPopup.reuseEmpty(this,empty.get(),quantity,expiry,values.get("Remarks"));
                if(choice==DuplicateBatchPopup.EmptyChoice.CANCEL)return;
                boolean refill=choice==DuplicateBatchPopup.EmptyChoice.REUSE;
                controller.saveAsync(()->{
                    if(refill)app.refillBatchSession(empty.get().getBatchId(),name).accept(values);
                    else app.inventoryBatchSession(name,true,false).accept(values);
                    return true;
                });
                return;
            }
            chooseMatchingBatch(values,name,expiry,quantity,true,controller,batches);
        });
    }
    private List<Medicine> medicineBatches(String name) {
        List<Medicine> batches=new ArrayList<>();int page=0,total;
        do {PageResult<Medicine> result=app.medicines(page++,100,name);batches.addAll(result.rows());total=result.total();}
        while(batches.size()<total);
        return batches;
    }
    private void checkBatch(Map<String,String> values,String name,boolean restock,FormPopup.SaveController controller) {
        int quantity=Integer.parseInt(values.get("Quantity"));LocalDate expiry=LocalDate.parse(values.get("Expiry (YYYY-MM-DD)"));
        Actions.run(this,()->app.matchingMedicineBatches(name,expiry),batches->{
            chooseMatchingBatch(values,name,expiry,quantity,restock,controller,batches);
        });
    }
    private void chooseMatchingBatch(Map<String,String> values,String name,LocalDate expiry,int quantity,
            boolean restock,FormPopup.SaveController controller,List<Medicine> batches) {
        Optional<BatchMatcher.Match> match=BatchMatcher.find(batches,name,expiry);
        boolean merge=false;
        if(match.isPresent()) {
            BatchMatcher.Match found=match.get();DuplicateBatchPopup.Choice choice;
            try { choice=DuplicateBatchPopup.show(this,found.batch(),found.count(),quantity,values.get("Remarks")); }
            catch(ArithmeticException overflow){MessagePopup.error(this,overflow);return;}
            if(choice==DuplicateBatchPopup.Choice.CANCEL)return;
            merge=choice==DuplicateBatchPopup.Choice.MERGE;
        }
        boolean mergeChoice=merge;
        controller.saveAsync(()->{app.inventoryBatchSession(name,restock,mergeChoice).accept(values);return true;});
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
            .append(group.name()).append(" — expiry ").append(group.expiry()).append('\n');
        group.batches().forEach(batch->details.append("Quantity ").append(batch.getQuantity()).append(", batch ")
            .append(batch.getBatchDate()).append(", remarks: ").append(batch.getRemarks().isBlank()?"(none)":batch.getRemarks()).append('\n'));
        if(!DuplicateBatchPopup.confirmGroup(this,details.toString())){confirmDuplicateGroup(groups,index+1);return;}
        Actions.run(this,()->{app.mergeDuplicateSession().accept(group);return true;},done->confirmDuplicateGroup(groups,index+1));
    }
    private void forecast() {
        Medicine medicine=table.selected();
        if(medicine==null){MessagePopup.warning(this,"Select a medicine.");return;}
        Actions.run(this,()->"Available unexpired stock: "+app.clinic().getStock(medicine.getName())+
            "\nEstimated days remaining: "+app.clinic().estimateDaysRemaining(medicine.getName())+
            " (-1 means no usage history)",message->MessagePopup.info(this,message));
    }
}
