package clinic.service;

import clinic.model.MedicineProduct;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Immutable, read-only catalog parsed from the workbook, never persisted as stock. */
public final class MedicineCatalog {
    public record Entry(String name,String atcPrimary,String atcSecondary,
            boolean whoEssential,boolean availableInPhilippines) {
        public MedicineProduct product(){return new MedicineProduct(name,atcPrimary,atcSecondary);}
    }
    private final List<Entry> entries;
    public MedicineCatalog(List<Entry> entries) {
        Map<String,Entry> unique=new LinkedHashMap<>();
        for(Entry entry:entries)if(!entry.name().isBlank()) {
            Entry clean=new Entry(entry.name().trim(),entry.atcPrimary().trim(),entry.atcSecondary().trim(),
                entry.whoEssential(),entry.availableInPhilippines());
            unique.putIfAbsent(clean.name().toLowerCase(Locale.ROOT),clean);
        }
        this.entries=List.copyOf(unique.values());
    }
    public static MedicineCatalog read(Path path)throws IOException{return CatalogWorkbook.read(path);}
    public List<Entry> entries(){return entries;}
    public Optional<Entry> findByName(String name) {
        String key=normalizedName(name);
        if(key.isBlank())return Optional.empty();
        return entries.stream().filter(entry->normalizedName(entry.name()).equals(key)).findFirst();
    }
    public List<Entry> search(String query) {
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        return entries.stream().filter(e->rank(e,q)<2)
            .sorted(Comparator.comparingInt((Entry e)->rank(e,q))
                .thenComparing(e->!e.availableInPhilippines()).thenComparing(Entry::name,String.CASE_INSENSITIVE_ORDER))
            .limit(50).toList();
    }
    private static int rank(Entry e,String q) {
        List<String> values=List.of(e.name(),e.atcPrimary(),e.atcSecondary());
        if(values.stream().anyMatch(s->s.toLowerCase(Locale.ROOT).startsWith(q)))return 0;
        return values.stream().anyMatch(s->s.toLowerCase(Locale.ROOT).contains(q))?1:2;
    }
    private static String normalizedName(String name) {
        if(name==null)return "";
        return name.trim().replaceFirst("(?i)\\s+\\d+(?:\\.\\d+)?\\s*(?:mcg|mg|ml|g|%)\\s*$","")
            .trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
    }
}
