package clinic.app.ui.common;

import clinic.service.MedicineCatalog;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** One background workbook load shared by every catalog picker. Call on the EDT. */
final class MedicineCatalogCache {
    private static MedicineCatalog catalog;
    private static Throwable failure;
    private static boolean loading;
    private static final List<Consumer<MedicineCatalog>> ready=new ArrayList<>();
    private static final List<Consumer<Throwable>> failed=new ArrayList<>();
    private MedicineCatalogCache() {}
    static void load(Consumer<MedicineCatalog> success,Consumer<Throwable> error) {
        if(catalog!=null){success.accept(catalog);return;}
        if(failure!=null){error.accept(failure);return;}
        ready.add(success);failed.add(error);if(loading)return;loading=true;
        AsyncLoader.load(()->MedicineCatalog.read(Path.of("resources","medicines.xlsx")),value->{
            catalog=value;loading=false;List.copyOf(ready).forEach(c->c.accept(value));ready.clear();failed.clear();
        },problem->{failure=problem;loading=false;List.copyOf(failed).forEach(c->c.accept(problem));ready.clear();failed.clear();});
    }
}
