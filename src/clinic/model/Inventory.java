package clinic.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Inventory {

    private List<Medicine> medicines;

    public Inventory() {
        this.medicines = new ArrayList<>();
    }

    public void addMed(String medicineName, int quantity, String expirationDate, String batchDate,
            String remarks, int medId) {
        Medicine med = new Medicine(medicineName, quantity, expirationDate, batchDate, remarks, medId);

        this.medicines.add(med);

        //extra functions here later
    }
    
    public void deleteMed(int medId){
        for (Medicine meds : medicines) {
            if (meds.medId == medId) {
               medicines.remove(meds);
               break;
            }
        }
    }

    public void subtractQuantity(int medId, int quantity) {
        for (Medicine meds : medicines) {
            if (meds.medId == medId) {
                if (meds.quantity >= quantity && quantity>0) {
                    meds.quantity -= quantity;
                    break;
                }else{
                    System.out.println("insufficient stock or quantity input is 0 or below");
                    break;
                }
            }
        }
    }

    public void addQuantity(int medId, int quantity) {
        for (Medicine meds : medicines) {
            if (meds.medId == medId) {
                if (quantity <= 0) {
                    System.out.println("Error: quantity cannot be 0 or below");//might be changed into a string
                } else {
                    meds.quantity += quantity;
                }
                break;
            }
        }
    }

    public void changeRemarks(int medId, String remarks) {
        for (Medicine meds : medicines) {
            if (meds.medId == medId) {
                meds.remarks = remarks;
                break;
            }
        }
    }

    public void changeMedicineName(int medId, String medicineName) {
        for (Medicine meds : medicines) {
            if (meds.medId == medId) {
                meds.medicineName = medicineName;
                break;
            }
        }
    }

    public void changeQuantity(int medId, int quantity) {
        for (Medicine meds : medicines) {
            if (meds.medId == medId) {
                meds.quantity = quantity;
                break;
            }
        }
    }

    public void changeExpirationDate(int medId, String expirationDate) {
        for (Medicine meds : medicines) {
            if (meds.medId == medId) {
                meds.expirationDate = LocalDate.parse(expirationDate);
                break;
            }
        }
    }

    public void changeBatchDate(int medId, String batchDate) {
        for (Medicine meds : medicines) {
            if (meds.medId == medId) {
                meds.batchDate = LocalDate.parse(batchDate);
                break;
            }
        }
    }
}

