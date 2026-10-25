import clinic.model.*;
import clinic.persistence.*;
import clinic.service.*;
import java.nio.file.*;
import java.sql.Statement;
import java.time.*;
import java.util.*;

/** Verifies SQL-paged dispense queries using a fresh temporary encrypted database. */
public final class PersistentDispenseChecks {
    static void check(String name,Object expected,Object actual){System.out.println(name+" | expected="+expected+" | actual="+actual);if(!Objects.equals(expected,actual))throw new AssertionError(name);}
    public static void main(String[] args)throws Exception {
        Path file=Files.createTempDirectory("scms-dispense-page-").resolve("clinic");
        try(Database database=new Database(file)) {
            ClinicData clinic=ClinicRepository.create(database);
            clinic.registerPatient(new Student("p","Synthetic","own","CS",1,"A"));
            database.transaction(c->{
                SqlTest.update(c,"INSERT INTO accounts(id,name,role,password_hash,active) VALUES('n','Nurse','NURSE','test',TRUE)");
                SqlTest.update(c,"INSERT INTO medicine_batches VALUES(0,'Paracetamol',5,?,?, '')",LocalDate.now().plusDays(10),LocalDate.now());
                SqlTest.update(c,"INSERT INTO medicine_batches VALUES(1,'Paracetamol',7,?,?, '')",LocalDate.now().plusDays(20),LocalDate.now());
                SqlTest.update(c,"INSERT INTO medicine_batches VALUES(2,'Expired',20,?,?, '')",LocalDate.now().minusDays(1),LocalDate.now().minusDays(40));
                SqlTest.update(c,"INSERT INTO medicine_batches VALUES(3,'Zero',0,?,?, '')",LocalDate.now().plusDays(4),LocalDate.now());
                for(int i=0;i<250;i++)SqlTest.update(c,"INSERT INTO dispensing(id,medicine,patient_id,staff_id,quantity,expiry,remarks,dispensed_at) VALUES(?,?,?,?,?,?,?,?)",
                    "t"+i,i%2==0?"Paracetamol":"Other","p","n",1,LocalDate.now().plusDays(20),"",LocalDateTime.now().minusSeconds(i));
                return null;
            });
            PageResult<Medicine> meds=clinic.dispensableMedicinePage(0,1000,null);
            check("SQL medicine grouping/filtering",List.of("Paracetamol:12","Zero:0"),meds.rows().stream().map(m->m.getName()+":"+m.getQuantity()).toList());
            check("SQL medicine count/page cap",List.of(2,100,0),List.of(meds.total(),meds.size(),meds.page()));
            check("SQL medicine partial search",1,clinic.dispensableMedicinePage(0,25,"para").total());
            PageResult<MedicineTransaction> first=clinic.transactionPage(0,25,"P",LocalDateTime.now().minusDays(1),null);
            check("SQL transaction search and date count",25,first.rows().size());
            check("SQL transaction page cap and metadata",List.of(100,0),List.of(clinic.transactionPage(0,1000,null,null,null).size(),clinic.transactionPage(0,1000,null,null,null).page()));
            check("SQL transactions newest first",true,first.rows().get(0).getDateTime().isAfter(first.rows().get(1).getDateTime()));
            PageResult<MedicineTransaction> outside=clinic.transactionPage(500,25,null,null,null);
            check("SQL out-of-range page",List.of(0,250,500,25),List.of(outside.rows().size(),outside.total(),outside.page(),outside.size()));
        }
        System.out.println("PASS: persistent dispense query checks.");
    }
    /** Tiny prepared-statement helper local to this standalone test. */
    private static final class SqlTest {
        static void update(java.sql.Connection c,String sql,Object... values)throws java.sql.SQLException {
            try(var s=c.prepareStatement(sql)){for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);s.executeUpdate();}
        }
    }
}
