package clinic.service;
import java.time.LocalDateTime;
import java.util.*;
import clinic.model.*;
public class PatternDetector {
    public List<Alert> detectOutbreak(List<MedicalRecord> records, List<Patient> patients, int days, int threshold) {
        return outbreakStream(records, patients, days, threshold).toList();
    }
    public java.util.stream.Stream<Alert> outbreakStream(List<MedicalRecord> records, List<Patient> patients, int days, int threshold) {
        if(records==null||patients==null||days<1||threshold<1)return java.util.stream.Stream.empty();
        Map<String, Patient> byId=new HashMap<>();
        patients.stream().filter(Objects::nonNull).forEach(p->byId.put(p.getId(), p));
        Map<String, Integer> groups=new TreeMap<>();
        LocalDateTime now=LocalDateTime.now();
        for(MedicalRecord r:records) {
            if(!inWindow(r,now,days))continue;
            Patient p=byId.get(r.getPatientId());
            String group=p instanceof Student s?"Student section "+normalize(s.getSection())
                :p instanceof Employee e?"Employee department "+normalize(e.getDepartment()):null;
            if(group!=null)for(String symptom:symptoms(r))groups.merge(group+" / "+symptom,1,Integer::sum);
        }
        return groups.entrySet().stream().filter(e->e.getValue()>=threshold).map(e->new Alert("OUTBREAK",
            e.getKey()+": "+e.getValue()+" visits; rule >= "+threshold+" same-symptom visits in "+(days*24L)+" hours",
            "outbreak:"+e.getKey()));
    }
    public Alert detectRecurringVisit(List<MedicalRecord> records, String patient, int days, int threshold) {
        if(records==null||patient==null||patient.isBlank()||days<1||threshold<1)return null;
        Map<String,Integer> counts=new TreeMap<>();
        LocalDateTime now=LocalDateTime.now();
        for(MedicalRecord r:records)if(inWindow(r,now,days)&&patient.equals(r.getPatientId())) {
            for(String symptom:symptoms(r))counts.merge("symptom:"+symptom,1,Integer::sum);
            String reason=normalize(r.getReason());
            if(!reason.isEmpty())counts.merge("reason:"+reason,1,Integer::sum);
        }
        int n=counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return n>=threshold?new Alert("RECURRING_VISIT", "Single-patient group: "+n+" visits; rule >= "+threshold
            +" visits with the same symptom or concern in "+days+" days","recurring:"+patient):null;
    }
    private boolean inWindow(MedicalRecord r,LocalDateTime now,int days) {
        return r!=null&&!r.getVisitDateTime().isBefore(now.minusDays(days))&&!r.getVisitDateTime().isAfter(now);
    }
    private Set<String> symptoms(MedicalRecord r) {
        Set<String> result=new HashSet<>();
        r.getSymptoms().stream().map(this::normalize).filter(FeatureRules.SYMPTOMS::contains).forEach(result::add);
        return result;
    }
    private String normalize(String s) {return s==null?"":s.trim().toLowerCase(Locale.ROOT);}
}
