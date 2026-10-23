package clinic.model;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MedicineTransaction {

    /*
    Note:
    
    needs to have association with medicine
    medT 1 to 1* med-done
    
    needs to have assosciation with patient
    medT 0* to 1 patient
    
    needs DAO
    
    needs get methods-done
    */
    
    public int transactionID;//to be assigned after it gets add to the database
    public int quantityGiven;
    public String distributor; //will be the user
    public LocalDate transDate;//will immedietly be LocalDateTime.now();

    private List<Medicine> medicines;

    public MedicineTransaction(int quantityGiven, String distributor) {
        this.quantityGiven = quantityGiven;
        this.distributor = distributor;
        this.medicines = new ArrayList<>();
        this.transDate = LocalDate.now();
    }

    public MedicineTransaction(Medicine med) {//adds a med
        medicines.add(med);
    }

    //will be used in the DAO
    public void setTransactionID(int transactionID) {
        this.transactionID = transactionID;
    }
    
    public int getTransactionID() {
        return transactionID;
    }

    public int getQuantityGiven() {
        return quantityGiven;
    }

    public String getDistributor() {
        return distributor;
    }

    public LocalDate getTransDate() {
        return transDate;
    }
}