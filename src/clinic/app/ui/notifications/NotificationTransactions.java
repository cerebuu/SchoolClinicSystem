package clinic.app.ui.notifications;

import clinic.app.ApplicationContext;
import clinic.model.MedicineTransaction;
import clinic.service.PageResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class NotificationTransactions {
    private NotificationTransactions() {}

    static List<MedicineTransaction> latestForPatient(ApplicationContext app,String patientId) {
        List<MedicineTransaction> matches=new ArrayList<>();int page=0,total;
        do {
            PageResult<MedicineTransaction> result=app.transactions(page,10,patientId,null,null);
            total=result.total();
            result.rows().stream().filter(tx->patientId.equals(tx.getPatientId())).forEach(matches::add);
            page++;
        } while(matches.size()<10&&(long)page*10<total&&!Thread.currentThread().isInterrupted());
        return matches.stream().sorted(Comparator.comparing(MedicineTransaction::getDateTime).reversed()
            .thenComparing(MedicineTransaction::getTransactionId)).limit(10).toList();
    }

    static Throwable cause(Exception error) {
        return error instanceof java.util.concurrent.ExecutionException&&error.getCause()!=null?error.getCause():error;
    }
}
