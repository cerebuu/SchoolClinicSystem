package clinic.model;
import java.time.LocalDate;

public class Medicine {

    /*
    Notes:
    
    example code
    LocalDate parsedDate = LocalDate.parse("2026-10-31");
    System.out.println(parsedDate); // Outputs: 2026-10-31
     */

    public String medicineName;
    public int quantity;
    public LocalDate expirationDate;
    public String remarks;
    public LocalDate batchDate;
    public int medId;
    //needs to have hard coded connection to inventory

    public Medicine(String medicineName, int quantity, String expirationDate, String batchDate,
            String remarks, int medId) {
        this.medicineName = medicineName;
        this.quantity = quantity;
        this.remarks = remarks;
        this.medId = medId;

        LocalDate parsedExpDate = LocalDate.parse(expirationDate);
        this.expirationDate = parsedExpDate;

        LocalDate parsedBatDate = LocalDate.parse(batchDate);
        this.batchDate = parsedBatDate;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public String getRemarks() {
        return remarks;
    }

    public LocalDate getBatchDate() {
        return batchDate;
    }
}
