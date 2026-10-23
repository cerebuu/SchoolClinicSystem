package clinic.service;
import java.time.LocalDate;
import java.util.*;
import clinic.model.Medicine;
import clinic.util.Validation;
public class Inventory {
    private final List<Medicine> batches=new ArrayList<>();
    void restoreBatches(List<Medicine> stored) {
        if (!batches.isEmpty()) throw new IllegalStateException("Inventory already initialized.");
        stored.forEach(m -> batches.add(copy(m)));
    }
    public synchronized void addMedicine(Medicine m) {
        Objects.requireNonNull(m);
        if(m.isExpired())throw new IllegalArgumentException("Expired batches cannot be added.");
        Math.addExact(getStock(m.getName()), m.getQuantity());
        batches.add(copy(m));
    }
    public synchronized void restockMedicine(String name, int qty, LocalDate expiry) {
        Validation.positive(qty);
        if(batches.stream().noneMatch(m->m.getName().equalsIgnoreCase(name)))throw new IllegalArgumentException("Medicine not found.");
        addMedicine(new Medicine(name, qty, expiry, LocalDate.now(), "Restock"));
    }
    public synchronized int getStock(String name) {
        return batches.stream().filter(m->m.getName().equalsIgnoreCase(name)&&!m.isExpired()).mapToInt(Medicine::getQuantity).reduce(0, Math::addExact);
    }
    private Medicine copy(Medicine medicine) {
        return new Medicine(medicine.getName(), medicine.getQuantity(), medicine.getExpirationDate(), medicine.getBatchDate(), medicine.getRemarks());
    }
    private Map<String, Integer> stockTotals() {
        Map<String, Integer> totals = new HashMap<>();
        for (Medicine medicine : batches) {
            if (!medicine.isExpired()) totals.merge(medicine.getName().toLowerCase(Locale.ROOT), medicine.getQuantity(), Math::addExact);
        }
        return totals;
    }
    public synchronized List<Medicine> getLowStock(int threshold) {
        if (threshold < 0) throw new IllegalArgumentException("Threshold must be nonnegative.");
        Map<String, Integer> totals = stockTotals();
        Map<String, Medicine> items = new LinkedHashMap<>();
        for (Medicine batch : batches) {
            String key = batch.getName().toLowerCase(Locale.ROOT);
            int total = totals.getOrDefault(key, 0);
            if (total <= threshold) items.putIfAbsent(key, new Medicine(batch.getName(), total,
                batch.getExpirationDate(), batch.getBatchDate(), "Total available stock"));
        }
        return List.copyOf(items.values());
    }
    public synchronized List<Medicine> getExpiringWithin(int days) {
        if(days<0)throw new IllegalArgumentException("Days must be nonnegative.");
        return batches.stream().filter(m->m.getQuantity()>0&&!m.getExpirationDate().isAfter(LocalDate.now().plusDays(days))).map(this::copy).toList();
    }
    public synchronized LocalDate dispense(String name, int qty) {
        Validation.positive(qty);
        if(qty>getStock(name))throw new IllegalArgumentException("Insufficient unexpired stock.");
        int remaining=qty;
        LocalDate earliest=null;
        for(Medicine m:batches.stream().filter(m->m.getName().equalsIgnoreCase(name)&&!m.isExpired()&&m.getQuantity()>0).sorted(Comparator.comparing(Medicine::getExpirationDate)).toList()) {
            if(earliest==null)earliest=m.getExpirationDate();
            int take=Math.min(remaining, m.getQuantity());
            m.reduceQuantity(take);
            remaining-=take;
            if(remaining==0)break;
        }
        return earliest;
    }
    public synchronized PageResult<Medicine> page(int page, int size, String query) {
        String q=query.toLowerCase(Locale.ROOT);
        PageResult<Medicine> result=PageResult.select(batches, page, size, m->m.getName().toLowerCase(Locale.ROOT).contains(q));
        return new PageResult<>(result.rows().stream().map(m->new Medicine(m.getName(), m.getQuantity(), m.getExpirationDate(), m.getBatchDate(), m.getRemarks())).toList(), result.total());
    }
    public synchronized void collectAlerts(java.util.function.Consumer<clinic.model.Alert> consumer) {
        Map<String, Integer> totals = stockTotals();
        Set<String> seen = new HashSet<>();
        for(Medicine m:batches) {
            if(totals.getOrDefault(m.getName().toLowerCase(Locale.ROOT), 0)<=10 && seen.add(m.getName().toLowerCase(Locale.ROOT)))consumer.accept(new clinic.model.Alert("LOW_STOCK", m.getName()));
            if(m.getQuantity()>0&&!m.getExpirationDate().isAfter(LocalDate.now().plusDays(30)))consumer.accept(new clinic.model.Alert("EXPIRING", m.getName()));
        }
    }
    public synchronized List<Medicine> batches() {
        return batches.stream().map(this::copy).toList();
    }
}
