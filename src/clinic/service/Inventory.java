package clinic.service;
import java.time.LocalDate;
import java.util.*;
import clinic.model.Medicine;
import clinic.model.MedicineProduct;
import clinic.model.BatchAllocation;
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
        Math.addExact(getStock(m.getProduct()), m.getQuantity());
        batches.add(copy(m));
    }
    public synchronized void restockMedicine(String name, int qty, LocalDate expiry) {
        restockMedicine(name, qty, expiry, "Restock");
    }
    public synchronized void restockMedicine(String name, int qty, LocalDate expiry, String remarks) {
        restockMedicine(new MedicineProduct(name,"",""),qty,expiry,remarks);
    }
    public synchronized void restockMedicine(MedicineProduct product,int qty,LocalDate expiry,String remarks) {
        Validation.positive(qty);
        if (expiry == null || !expiry.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Expiry must be a future date (YYYY-MM-DD).");
        String normalized = remarks == null ? "" : remarks.trim();
        if (normalized.length() > 200) throw new IllegalArgumentException("Remarks must be at most 200 characters.");
        if(batches.stream().noneMatch(m->m.getProduct().key().equals(product.key())))throw new IllegalArgumentException("Medicine not found.");
        addMedicine(new Medicine(product, qty, expiry, LocalDate.now(), normalized));
    }
    public synchronized List<Medicine> matchingBatches(String name,LocalDate expiry) {
        return matchingBatches(new MedicineProduct(name,"",""),expiry);
    }
    public synchronized List<Medicine> matchingBatches(MedicineProduct product,LocalDate expiry) {
        return batches.stream().filter(m->m.getProduct().key().equals(product.key())
            &&m.getExpirationDate().equals(expiry)&&!m.isExpired())
            .sorted(Comparator.comparing(Medicine::getBatchDate)).map(this::copy).toList();
    }
    public synchronized void mergeBatch(String name,int quantity,LocalDate expiry,String remarks) {
        mergeBatch(new MedicineProduct(name,"",""),quantity,expiry,remarks);
    }
    public synchronized void mergeBatch(MedicineProduct product,int quantity,LocalDate expiry,String remarks) {
        Validation.positive(quantity);
        if(expiry==null||expiry.isBefore(LocalDate.now()))throw new IllegalArgumentException("Expiry must not be in the past.");
        Medicine target=BatchMatcher.find(batches,product,expiry).map(BatchMatcher.Match::batch)
            .orElseThrow(()->new IllegalArgumentException("No matching unexpired batch was found."));
        String merged=BatchMatcher.mergedRemarks(target.getRemarks(),remarks,LocalDate.now());
        Math.addExact(getStock(product),quantity);
        Math.addExact(target.getQuantity(),quantity);
        target.addQuantity(quantity);
        target.setRemarks(merged);
    }
    public synchronized void mergeDuplicateBatches(String name,LocalDate expiry) {
        mergeDuplicateBatches(new MedicineProduct(name,"",""),expiry);
    }
    public synchronized void mergeDuplicateBatches(MedicineProduct product,LocalDate expiry) {
        List<Medicine> found=batches.stream().filter(m->m.getProduct().key().equals(product.key())
            &&m.getExpirationDate().equals(expiry)&&!m.isExpired())
            .sorted(Comparator.comparing(Medicine::getBatchDate)).toList();
        if(found.size()<2)throw new IllegalArgumentException("No duplicate batches remain to merge.");
        Medicine target=found.get(0);
        int total=target.getQuantity();
        String remarks=target.getRemarks();
        for(int i=1;i<found.size();i++) {
            Medicine batch=found.get(i);
            total=Math.addExact(total,batch.getQuantity());
            if(!batch.getRemarks().isBlank()&&!batch.getRemarks().equals(remarks))
                remarks=BatchMatcher.mergedRemarks(remarks,batch.getRemarks(),batch.getBatchDate());
        }
        if(total>target.getQuantity())target.addQuantity(total-target.getQuantity());
        target.setRemarks(remarks);
        batches.removeAll(found.subList(1,found.size()));
    }
    public synchronized void refillBatch(String batchId,int quantity,LocalDate expiry,String remarks) {
        Medicine batch=batches.stream().filter(m->m.getBatchId().equals(batchId)).findFirst()
            .orElseThrow(()->new IllegalArgumentException("Unknown batch ID."));
        if(batch.getQuantity()!=0)throw new IllegalStateException("Batch still has stock");
        Math.addExact(getStock(batch.getProduct()),quantity);
        batch.refillFromEmpty(quantity,expiry,remarks);
    }
    public synchronized int getStock(String name) {
        return batches.stream().filter(m->m.getName().equalsIgnoreCase(name)&&!m.isExpired()).mapToInt(Medicine::getQuantity).reduce(0, Math::addExact);
    }
    public synchronized int getStock(MedicineProduct product) {
        return batches.stream().filter(m->m.getProduct().key().equals(product.key())&&!m.isExpired())
            .mapToInt(Medicine::getQuantity).reduce(0,Math::addExact);
    }
    private Medicine copy(Medicine medicine) {
        return new Medicine(medicine.getBatchId(),medicine.getProduct(),medicine.getQuantity(),
            medicine.getExpirationDate(),medicine.getBatchDate(),medicine.getRemarks());
    }
    private Map<String, Integer> stockTotals() {
        Map<String, Integer> totals = new HashMap<>();
        for (Medicine medicine : batches) {
            if (!medicine.isExpired()) totals.merge(medicine.getProduct().key(), medicine.getQuantity(), Math::addExact);
        }
        return totals;
    }
    public synchronized List<Medicine> getLowStock(int threshold) {
        if (threshold < 0) throw new IllegalArgumentException("Threshold must be nonnegative.");
        Map<String, Integer> totals = stockTotals();
        Map<String, Medicine> items = new LinkedHashMap<>();
        for (Medicine batch : batches) {
            String key = batch.getProduct().key();
            int total = totals.getOrDefault(key, 0);
            Medicine available=new Medicine(batch.getProduct(),total,batch.getExpirationDate(),batch.getBatchDate(),"Total available stock");
            if (available.isLowStock(threshold)) items.putIfAbsent(key,available);
        }
        return List.copyOf(items.values());
    }
    public synchronized List<Medicine> getExpiringWithin(int days) {
        if(days<0)throw new IllegalArgumentException("Days must be nonnegative.");
        return batches.stream().filter(m->m.getQuantity()>0&&!m.isExpired()&&!m.getExpirationDate().isAfter(LocalDate.now().plusDays(days)))
            .sorted(Comparator.comparing(Medicine::getExpirationDate)).map(this::copy).toList();
    }
    public synchronized LocalDate dispense(String name, int qty) {
        return dispense(new MedicineProduct(name,"",""),qty);
    }
    public synchronized LocalDate dispense(MedicineProduct product,int qty) {
        List<BatchAllocation> allocations=dispenseAllocations(product,qty);
        return allocations.get(0).expiry();
    }
    public synchronized List<BatchAllocation> dispenseAllocations(MedicineProduct product,int qty) {
        Validation.positive(qty);
        if(qty>getStock(product))throw new IllegalArgumentException("Insufficient unexpired stock.");
        int remaining=qty;
        List<BatchAllocation> allocations=new ArrayList<>();
        for(Medicine m:batches.stream().filter(m->m.getProduct().key().equals(product.key())&&!m.isExpired()&&m.getQuantity()>0)
                .sorted(Comparator.comparing(Medicine::getExpirationDate).thenComparing(Medicine::getBatchDate)
                    .thenComparing(Medicine::getBatchId)).toList()) {
            int take=Math.min(remaining, m.getQuantity());
            m.reduceQuantity(take);
            allocations.add(new BatchAllocation(m.getBatchId(),take,m.getExpirationDate()));
            remaining-=take;
            if(remaining==0)break;
        }
        return List.copyOf(allocations);
    }
    public synchronized PageResult<Medicine> page(int page, int size, String query) {
        String q=query.toLowerCase(Locale.ROOT);
        PageResult<Medicine> result=PageResult.select(batches, page, size,
            m->m.getDisplayName().toLowerCase(Locale.ROOT).contains(q));
        return new PageResult<>(result.rows().stream().map(this::copy).toList(), result.total());
    }
    public synchronized void collectAlerts(java.util.function.Consumer<clinic.model.Alert> consumer) {
        getLowStock(FeatureRules.LOW_STOCK_THRESHOLD).forEach(m->consumer.accept(new clinic.model.Alert("LOW_STOCK",
            m.getName()+": "+m.getQuantity()+" usable units; threshold <= "+FeatureRules.LOW_STOCK_THRESHOLD)));
        getExpiringWithin(FeatureRules.EXPIRY_DAYS).forEach(m->consumer.accept(new clinic.model.Alert("EXPIRING",
            m.getName()+": "+m.getQuantity()+" units expire "+m.getExpirationDate()+"; within "+FeatureRules.EXPIRY_DAYS+" days")));
        batches.stream().filter(m->m.isExpired()&&m.getQuantity()>0).forEach(m->consumer.accept(new clinic.model.Alert("EXPIRED",
            m.getName()+": "+m.getQuantity()+" unusable units expired "+m.getExpirationDate())));
    }
    public synchronized List<Medicine> batches() {
        return batches.stream().map(this::copy).toList();
    }
}
