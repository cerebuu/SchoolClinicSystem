package clinic.service;
import clinic.model.*;
import java.util.*;

final class AlertCollector {
    private AlertCollector() {}
    static List<Alert> collect(List<MedicalRecord> records,Collection<Patient> patients,Inventory inventory) {
        PatternDetector detector=new PatternDetector();
        List<Alert> alerts=new ArrayList<>(detector.detectOutbreak(records,new ArrayList<>(patients),
            FeatureRules.OUTBREAK_DAYS,FeatureRules.VISIT_THRESHOLD));
        patients.forEach(p->{
            Alert recurring=detector.detectRecurringVisit(records,p.getId(),FeatureRules.RECURRING_DAYS,FeatureRules.VISIT_THRESHOLD);
            if(recurring!=null)alerts.add(recurring);
        });
        inventory.collectAlerts(alerts::add);
        alerts.sort(Comparator.comparing(Alert::getCreatedAt).reversed());
        return List.copyOf(alerts);
    }
    static PageResult<Alert> page(List<Alert> alerts,int page,int size,String query) {
        return PageResult.select(alerts,page,size,a->query==null||query.isBlank()
            ||a.toString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)));
    }
}
