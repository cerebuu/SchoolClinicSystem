package clinic.service;
import clinic.util.Validation;
public record DispenseItem(String medicine,int quantity) {
    public DispenseItem {
        medicine=Validation.required(medicine,"Medicine");
        Validation.positive(quantity);
    }
    public String toString(){return medicine+" x "+quantity;}
}
