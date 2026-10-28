package clinic.app.ui.inventory;

import clinic.app.ApplicationContext;
import clinic.model.Medicine;
import clinic.model.MedicineProduct;
import clinic.service.PageResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Builds the grouped catalog from the existing authorized batch query. */
final class InventoryCatalog {
    private static final int BATCH_PAGE_SIZE = 500;

    private InventoryCatalog() {}

    static PageResult<InventoryProductRow> page(ApplicationContext app, int page, int size, String query) {
        String filter = query == null ? "" : query.trim();
        Map<String, List<Medicine>> grouped = new TreeMap<>();
        int batchPage = 0;
        int total;
        do {
            PageResult<Medicine> result = app.medicines(batchPage++, BATCH_PAGE_SIZE, filter);
            total = result.total();
            result.rows().forEach(batch -> grouped.computeIfAbsent(batch.getProduct().key(),
                ignored -> new ArrayList<>()).add(batch));
            if (result.rows().isEmpty() && (long) batchPage * BATCH_PAGE_SIZE < total) {
                throw new IllegalStateException("Unable to load the full inventory catalog.");
            }
        } while ((long) batchPage * BATCH_PAGE_SIZE < total);

        List<InventoryProductRow> products = grouped.values().stream()
            .map(InventoryCatalog::summarize)
            .toList();
        return PageResult.select(products, page, size, row -> filter.isEmpty()
            || (row.product().name()+" "+row.product().atcLabel()).toLowerCase(java.util.Locale.ROOT)
                .contains(filter.toLowerCase(java.util.Locale.ROOT)));
    }

    private static InventoryProductRow summarize(List<Medicine> batches) {
        List<Medicine> ordered = batches.stream()
            .sorted(Comparator.comparing(Medicine::getExpirationDate)
                .thenComparing(Medicine::getBatchDate).thenComparing(Medicine::getBatchId))
            .toList();
        MedicineProduct product = ordered.stream().map(Medicine::getProduct)
            .filter(p->!p.atcPrimary().isBlank()).findFirst().orElse(ordered.get(0).getProduct());
        int eligibleQuantity = 0;
        LocalDate nextExpiry = null;
        for (Medicine batch : ordered) {
            if (batch.isExpired()) continue;
            eligibleQuantity = Math.addExact(eligibleQuantity, batch.getQuantity());
            if (batch.getQuantity() > 0
                    && (nextExpiry == null || batch.getExpirationDate().isBefore(nextExpiry))) {
                nextExpiry = batch.getExpirationDate();
            }
        }
        return new InventoryProductRow(product, eligibleQuantity, nextExpiry, ordered);
    }
}
