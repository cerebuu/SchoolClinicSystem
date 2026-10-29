package clinic.service;
import clinic.model.*;
import java.time.*;
import java.util.*;

/** Data-source queries; callers receive only a capped page. */
public final class DispenseQueries {
    private DispenseQueries() {}
    public static int size(int requested) {
        if(requested<1)throw new IllegalArgumentException("Page size must be positive.");
        return Math.min(100,requested);
    }
    public static String query(String q){return q==null?"":q.trim().toLowerCase(Locale.ROOT);}
    public static <T> PageResult<T> page(List<T> sorted,int page,int size) {
        int limit=size(size);
        long start=(long)page*limit;
        return new PageResult<>(page<0||start>=sorted.size()?List.of():sorted.subList((int)start,
            (int)Math.min(start+limit,sorted.size())),sorted.size(),page,limit);
    }
    public static PageResult<Medicine> medicines(List<Medicine> batches,int page,int size,String query) {
        String q=query(query);
        LocalDate today=LocalDate.now();
        Map<String,Medicine> grouped=new TreeMap<>();
        for(Medicine m:batches)if((m.getName()+" "+m.getProduct().atcLabel()).toLowerCase(Locale.ROOT).contains(q)) {
            String key=m.getProduct().key();
            Medicine old=grouped.get(key);
            int usable=m.isExpired()?0:m.getQuantity();
            int oldUsable=old==null?0:old.getQuantity();
            boolean hasUsable=usable>0,oldHasUsable=oldUsable>0;
            boolean earlier=old==null||hasUsable&&!oldHasUsable
                ||hasUsable==oldHasUsable&&m.getExpirationDate().isBefore(old.getExpirationDate());
            int total=Math.addExact(oldUsable,usable);
            grouped.put(key,new Medicine(old!=null&&!old.getProduct().atcPrimary().isBlank()?old.getProduct():m.getProduct(),
                total,
                earlier?m.getExpirationDate():old.getExpirationDate(),
                m.getBatchDate(),"Available unexpired stock"));
        }
        return page(new ArrayList<>(grouped.values()),page,size);
    }
    public static PageResult<MedicineTransaction> transactions(Collection<MedicineTransaction> records,int page,int size,
            String query,LocalDateTime from,LocalDateTime to) {
        return transactions(records,page,size,query,from,to,null);
    }
    public static PageResult<MedicineTransaction> transactions(Collection<MedicineTransaction> records,int page,int size,
            String query,LocalDateTime from,LocalDateTime to,String campusId) {
        if(page<0)throw new IllegalArgumentException("Invalid page.");
        String q=query(query);
        List<MedicineTransaction> filtered=records.stream().filter(t->(t.getPatientId().toLowerCase(Locale.ROOT).contains(q)
            ||t.getMedicineDisplayName().toLowerCase(Locale.ROOT).contains(q))
            &&(campusId==null||campusId.equals(t.getCampusId()))
            &&(from==null||!t.getDateTime().isBefore(from))&&(to==null||t.getDateTime().isBefore(to)))
            .toList();
        return page(TimeOrder.newestFirst(filtered, MedicineTransaction::getDateTime),page,size);
    }
}
