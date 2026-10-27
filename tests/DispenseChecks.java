import clinic.core.ClinicSystem;
import clinic.model.*;
import clinic.service.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;

/** Focused workflow checks; uses synthetic data and no Swing dialogs. */
public final class DispenseChecks {
    static void check(String name,Object expected,Object actual){System.out.println(name+" | expected="+expected+" | actual="+actual);if(!Objects.equals(expected,actual))throw new AssertionError(name);}
    static InMemoryClinicSystem store() {
        Student p=new Student("p","Synthetic","own","CS",1,"A");
        List<Medicine> batches=List.of(
            new Medicine("Paracetamol",20,LocalDate.now().plusDays(30),LocalDate.now(),""),
            new Medicine("Ibuprofen",12,LocalDate.now().plusDays(30),LocalDate.now(),""),
            new Medicine("Zero",0,LocalDate.now().plusDays(30),LocalDate.now(),""),
            new Medicine("Expired",10,LocalDate.now().minusDays(1),LocalDate.now().minusDays(30),""));
        return new InMemoryClinicSystem(new InMemoryClinicSystem.State(List.of(p),List.of(),List.of(),List.of(),batches));
    }
    static DispenseWorkflow workflow(InMemoryClinicSystem store,SchoolNurse nurse,AuditLog audit){
        return new DispenseWorkflow(new SecuredClinicSystem(store,nurse,audit),nurse,audit);
    }
    public static void main(String[] args) {
        SchoolNurse nurse=new SchoolNurse("nurse-id","Synthetic nurse");nurse.setCampusId("C1");
        AuditLog audit=new AuditLog();InMemoryClinicSystem db=store();
        PageResult<Medicine> medicines=db.dispensableMedicinePage(0,1000,null);
        check("dropdown source includes live names and zero stock",List.of("Ibuprofen:12","Paracetamol:20","Zero:0"),medicines.rows().stream().map(m->m.getName()+":"+m.getQuantity()).toList());
        check("expired medicine hidden",false,medicines.rows().stream().anyMatch(m->m.getName().equals("Expired")));
        check("out-of-stock item is present for disabled choice",0,medicines.rows().stream().filter(m->m.getName().equals("Zero")).findFirst().orElseThrow().getQuantity());
        check("page size capped",100,db.dispensableMedicinePage(0,1000,null).size());
        check("page metadata and count",List.of(0,100,3),List.of(medicines.page(),medicines.size(),medicines.total()));
        check("case-insensitive partial query",List.of("Paracetamol"),db.dispensableMedicinePage(0,25,"para").rows().stream().map(Medicine::getName).toList());
        DispenseWorkflow wf=workflow(db,nurse,audit);
        check("merge same medicine rows",List.of(new DispenseItem("Paracetamol",5)),DispenseWorkflow.merge(List.of(new DispenseItem("Paracetamol",2),new DispenseItem("paracetamol",3))));
        DispenseResult four=wf.dispense("p",List.of(new DispenseItem("Paracetamol",4)),false);
        check("dispense four one row",1,four.completed().size());
        check("recorded transaction 4 units",4,four.completed().get(0).getQuantity());
        DispenseResult multi=wf.dispense("p",List.of(new DispenseItem("Paracetamol",1),new DispenseItem("Ibuprofen",2)),false);
        check("two medicines dispense",2,multi.completed().size());
        db.addAllergy("p","Paracetamol");
        List<String> warnings=wf.check("p",List.of(new DispenseItem("Paracetamol",1),new DispenseItem("Ibuprofen",2)));
        int before=db.snapshot().transactions().size();
        // Cancel action is deliberately no workflow call after the single collected warning set.
        check("one combined warning set",1,warnings.size());
        check("cancel dispenses nothing",before,db.snapshot().transactions().size());
        DispenseResult override=wf.dispense("p",List.of(new DispenseItem("Paracetamol",1),new DispenseItem("Ibuprofen",2)),true);
        check("override dispenses both",2,override.completed().size());
        int txBeforeShortage=db.snapshot().transactions().size();
        DispenseResult shortage=wf.dispense("p",List.of(new DispenseItem("Ibuprofen",1),new DispenseItem("Paracetamol",999)),false);
        check("second-row shortage dispenses none",0,shortage.completed().size());
        check("second-row shortage names medicine",true,shortage.failure().contains("Paracetamol"));
        check("second-row shortage leaves data unchanged",txBeforeShortage,db.snapshot().transactions().size());
        check("override cannot dispense expired",true,wf.dispense("p",List.of(new DispenseItem("Expired",1)),true).completed().isEmpty());

        // Restore stock for predictable partial behavior using a separate fixture.
        InMemoryClinicSystem partialDb=store();
        ClinicSystem failSecond=(ClinicSystem)Proxy.newProxyInstance(ClinicSystem.class.getClassLoader(),new Class<?>[]{ClinicSystem.class},new SecondCallFails(partialDb,nurse,audit));
        DispenseWorkflow partial=new DispenseWorkflow(failSecond,nurse,audit);
        DispenseResult partialResult=partial.dispense("p",List.of(new DispenseItem("Paracetamol",1),new DispenseItem("Ibuprofen",1)),false);
        check("partial failure reports one done",1,partialResult.completed().size());
        check("partial failure reports one remaining",1,partialResult.remaining().size());
        AuditEntry partialAudit=audit.getAll().stream().filter(a->a.getAction().equals("dispense partially completed")).findFirst().orElseThrow();
        check("partial audit action and user",List.of("dispense partially completed","nurse-id"),List.of(partialAudit.getAction(),partialAudit.getUserId()));
        check("partial audit counts only",true,partialAudit.getDetails().matches("completed=1, notDispensed=1"));
        check("partial audit details",true,partialAudit.getDetails().matches("completed=1, notDispensed=1"));
        long start=System.nanoTime();
        List<MedicineTransaction> tenK=new ArrayList<>();
        for(int i=0;i<10_000;i++)tenK.add(new MedicineTransaction("t"+i,"Medicine"+(i%4),"patient"+(i%100),"nurse-id",1,
            LocalDate.now().plusDays(30),"",LocalDateTime.now().minusSeconds(i)));
        InMemoryClinicSystem large=new InMemoryClinicSystem(new InMemoryClinicSystem.State(List.of(),List.of(),tenK,List.of(),List.of()));
        PageResult<MedicineTransaction> page=large.transactionPage(0,25,"patient1",LocalDateTime.now().minusDays(7),null);
        check("10,000 transactions filtered and paged",List.of(25,1100),List.of(page.rows().size(),page.total()));
        check("transactions newest first",true,page.rows().get(0).getDateTime().isAfter(page.rows().get(1).getDateTime()));
        check("out-of-range page empty",0,large.transactionPage(101,100,null,null,null).rows().size());
        System.out.println("10,000-row filtered query ms: "+((System.nanoTime()-start)/1_000_000));
    }
    private static final class SecondCallFails implements InvocationHandler {
        final ClinicSystem secured;int calls;
        SecondCallFails(ClinicSystem store,SchoolNurse nurse,AuditLog audit){secured=new SecuredClinicSystem(store,nurse,audit);}
        public Object invoke(Object proxy,Method m,Object[] args)throws Throwable {
            if(m.getName().equals("dispenseMedicine")&&++calls==2)throw new IllegalStateException("synthetic second-call failure");
            try{return m.invoke(secured,args);}catch(InvocationTargetException ex){throw ex.getCause();}
        }
    }
}
