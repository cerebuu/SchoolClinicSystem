import clinic.service.MedicineCatalog;
import java.nio.file.Path;

/** Read-only smoke check of the real workbook; no catalog rows are saved as stock. */
public final class MedicineCatalogDemo {
    public static void main(String[] args)throws Exception {
        MedicineCatalog catalog=MedicineCatalog.read(Path.of("resources","medicines.xlsx"));
        System.out.println("Catalog unique medicines="+catalog.entries().size());
        System.out.println("WHO essential="+catalog.entries().stream().filter(MedicineCatalog.Entry::whoEssential).count());
        System.out.println("Available in Philippines="+catalog.entries().stream().filter(MedicineCatalog.Entry::availableInPhilippines).count());
        catalog.search("paracetamol").forEach(e->System.out.println(e.name()+" | ATC="+e.product().atcLabel()
            +" | WHO="+e.whoEssential()+" | Philippines="+e.availableInPhilippines()));
    }
}
