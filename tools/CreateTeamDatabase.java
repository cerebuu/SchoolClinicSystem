import clinic.app.ApplicationContext;
import java.nio.file.Files;
import java.nio.file.Path;

/** Creates a new, empty encrypted database using the application's own schema. */
public class CreateTeamDatabase {
    public static void main(String[] args) throws Exception {
        Path target = Path.of("data", "team-starter", "scms").toAbsolutePath();
        if (Files.exists(Path.of(target + ".mv.db")) || Files.exists(Path.of(target + ".key")))
            throw new IllegalStateException("Starter already exists; refusing to overwrite it.");
        try (ApplicationContext app = new ApplicationContext(target)) {
            if (!app.login.needsSetup()) throw new IllegalStateException("Expected an empty account store.");
        }
        try (ApplicationContext app = new ApplicationContext(target)) {
            if (!app.login.needsSetup()) throw new IllegalStateException("Unexpected accounts after reopening.");
        }
        System.out.println("Created and reopened empty encrypted database: " + target + ".mv.db");
    }
}
