package clinic.service;
import java.time.LocalDateTime;
import java.util.List;
import clinic.model.*;
public class RestockForecast {
    public double calculateUsageRate(String medicine, List<MedicineTransaction> history, int days) {
        if(days<1)throw new IllegalArgumentException("Days must be positive.");
        return history.stream().filter(t->t.getMedicineName().equalsIgnoreCase(medicine)&&!t.getDateTime().isBefore(LocalDateTime.now().minusDays(days))).mapToLong(MedicineTransaction::getQuantity).sum()/(double)days;
    }
    public double estimateDaysRemaining(int stock, double rate) {
        return rate==0?-1:stock/rate;
    }
}
