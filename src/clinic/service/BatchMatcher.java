package clinic.service;

import clinic.model.Medicine;
import java.time.LocalDate;
import java.util.*;

/** Shared matching and duplicate-group rules for medicine batches. */
public final class BatchMatcher {
    private BatchMatcher() {}
    public record Match(Medicine batch,int count) {}
    public record Group(String name,LocalDate expiry,List<Medicine> batches) {
        public Group { batches=List.copyOf(batches); }
    }
    public static Optional<Match> find(Collection<Medicine> batches,String name,LocalDate expiry) {
        List<Medicine> found=batches.stream().filter(m->sameName(m.getName(),name)
            &&m.getExpirationDate().equals(expiry)&&!m.isExpired())
            .sorted(Comparator.comparing(Medicine::getBatchDate)).toList();
        return found.isEmpty()?Optional.empty():Optional.of(new Match(found.get(0),found.size()));
    }
    public static Optional<Medicine> depleted(Collection<Medicine> batches,String name,String selectedBatchId) {
        if(selectedBatchId!=null) {
            return batches.stream().filter(m->m.getBatchId().equals(selectedBatchId)
                &&m.getQuantity()==0&&sameName(m.getName(),name)).findFirst();
        }
        return batches.stream().filter(m->m.getQuantity()==0&&sameName(m.getName(),name))
            .max(Comparator.comparing(Medicine::getBatchDate));
    }
    public static List<Group> duplicates(Collection<Medicine> batches) {
        Map<String,List<Medicine>> groups=new TreeMap<>();
        for(Medicine batch:batches) if(!batch.isExpired()) {
            String key=batch.getName().trim().toLowerCase(Locale.ROOT)+"\u0000"+batch.getExpirationDate();
            groups.computeIfAbsent(key,k->new ArrayList<>()).add(batch);
        }
        return groups.values().stream().filter(g->g.size()>1)
            .map(g->new Group(g.get(0).getName(),g.get(0).getExpirationDate(),g.stream()
                .sorted(Comparator.comparing(Medicine::getBatchDate)).toList())).toList();
    }
    public static boolean sameName(String a,String b) {
        return a!=null&&b!=null&&a.trim().equalsIgnoreCase(b.trim());
    }
    public static String mergedRemarks(String oldValue,String newValue,LocalDate date) {
        String oldText=oldValue==null?"":oldValue.trim(), next=newValue==null?"":newValue.trim();
        if(next.isEmpty()||oldText.equals(next))return oldText;
        String suffix=date+" "+next;
        if(suffix.length()>200)throw new IllegalArgumentException("New remarks are too long to append with the date. Keep this as a separate batch.");
        String combined=oldText.isEmpty()?suffix:oldText+"; "+suffix;
        return combined.length()<=200?combined:combined.substring(combined.length()-200);
    }
}
