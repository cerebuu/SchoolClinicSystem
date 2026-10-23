package clinic.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Consultation {

    /*
    Notes:
    
    assosiation with medicineTransaction
    consul 1 to 0* Medt
    
    needs DAO
    
    needs get methods
     */
    public String symtomps;//will be used to search patterns
    public String healthConcern;
    public String remarks;
    public LocalDate date;

    private List<MedicineTransaction> transactions;

    public Consultation(String symtomps, String healthConcern, String remarks,
            String date) {
        this.symtomps = symtomps;
        this.healthConcern = healthConcern;
        this.remarks = remarks;

        LocalDate parsedDate = LocalDate.parse(date);
        this.date = parsedDate;
        
        this.transactions = new ArrayList();
    }

    public void addMedTrans(MedicineTransaction medTrans){
        this.transactions.add(medTrans);
    }
    
    
}
