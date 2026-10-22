package clinic.service;
import java.time.LocalDateTime;
import java.util.*;
import clinic.model.*;
public class PatternDetector {
    public List<Alert> detectOutbreak(List<MedicalRecord> records, List<Patient> patients, int days, int threshold) {
        return outbreakStream(records, patients, days, threshold).toList();
    }
    public java.util.stream.Stream<Alert> outbreakStream(List<MedicalRecord> records, List<Patient> patients, int days, int threshold) {
        Map<String, Patient> byId=new HashMap<>();
        patients.forEach(p->byId.put(p.getId(), p));
        Map<String, Set<String>> groups=new TreeMap<>();
        for(MedicalRecord r:records)if(!r.getVisitDateTime().isBefore(LocalDateTime.now().minusDays(days))&&byId.get(r.getPatientId()) instanceof Student s) for(String symptom:r.getSymptoms())groups.computeIfAbsent(s.getSection()+" / "+symptom.toLowerCase(Locale.ROOT), k->new HashSet<>()).add(s.getId());
        return groups.entrySet().stream().filter(e->e.getValue().size()>=threshold).map(e->new Alert("OUTBREAK", e.getKey()+": "+e.getValue().size()+" distinct patients"));
    }
    public Alert detectRecurringVisit(List<MedicalRecord> records, String patient, int days, int threshold) {
        long n=records.stream().filter(r->r.getPatientId().equals(patient)&&!r.getVisitDateTime().isBefore(LocalDateTime.now().minusDays(days))).count();
        return n>=threshold?new Alert("RECURRING_VISIT", n+" visits in "+days+" days"):null;
    }
}
