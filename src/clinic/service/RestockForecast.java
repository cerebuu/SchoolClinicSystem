package clinic.service;
import java.time.LocalDateTime;
import java.util.List;
import clinic.model.*;
public class RestockForecast {
    public double calculateUsageRate(String medicine, List<MedicineTransaction> history, int days) {
        if(days<1)throw new IllegalArgumentException("Days must be positive.");
        LocalDateTime now=LocalDateTime.now();
        return history.stream().filter(t->t.getMedicineName().equalsIgnoreCase(medicine)&&!t.getDateTime().isBefore(now.minusDays(days))&&!t.getDateTime().isAfter(now)).mapToLong(MedicineTransaction::getQuantity).sum()/(double)days;
    }
    public double estimateDaysRemaining(int stock, double rate) {
        return rate==0?Double.POSITIVE_INFINITY:stock/rate;
    }
    public double estimate(String name,Inventory inventory,List<MedicineTransaction> history) {
        if(inventory.batches().stream().noneMatch(m->m.getName().equalsIgnoreCase(name)))
            throw new IllegalArgumentException("Medicine not found: "+name);
        return estimateDaysRemaining(inventory.getStock(name),calculateUsageRate(name,history,FeatureRules.USAGE_DAYS));
    }
}
