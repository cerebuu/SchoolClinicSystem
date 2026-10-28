package clinic.app.ui.common;

import clinic.app.ApplicationContext;
import clinic.model.Medicine;
import clinic.service.MedicineCatalog;
import clinic.service.PageResult;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Loads bounded pages for MedicinePicker and keeps only a small page cache. */
final class MedicinePickerPageLoader {
    static final int PAGE_SIZE=25;
    private static final int MAX_CACHED_PAGES=4;
    private final ApplicationContext app;
    private final boolean catalogMode;
    private final Map<String,PageResult<Medicine>> cache=new LinkedHashMap<>();
    private MedicineCatalog catalog;

    MedicinePickerPageLoader(ApplicationContext app,boolean catalogMode) {
        this.app=app;this.catalogMode=catalogMode;
    }
    void clear() { cache.clear(); }

    void load(int page,String query,int version,BooleanSupplier active,
            Consumer<PageResult<Medicine>> success,Consumer<Throwable> failure) {
        if(catalogMode) {
            if(catalog==null) {
                MedicineCatalogCache.load(value->{catalog=value;load(page,query,version,active,success,failure);},failure);
                return;
            }
            var rows=catalog.search(query).stream().map(entry->new Medicine(entry.product(),1,
                LocalDate.MAX,LocalDate.now(),"")).toList();
            success.accept(new PageResult<>(rows,rows.size()));return;
        }
        String key=page+"\n"+query;PageResult<Medicine> cached=cache.get(key);
        if(cached!=null){success.accept(cached);return;}
        AsyncLoader.load(()->app.dispensableMedicines(page,PAGE_SIZE,query),result->{
            if(!active.getAsBoolean())return;
            if(cache.size()>=MAX_CACHED_PAGES)cache.remove(cache.keySet().iterator().next());
            cache.put(key,result);success.accept(result);
        },problem->{if(active.getAsBoolean())failure.accept(problem);});
    }
}
