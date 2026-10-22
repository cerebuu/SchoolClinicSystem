import clinic.model.Student;

/** Demo-only legacy row: models still require sections for newly registered students. */
public final class DemoStudentWithoutSection extends Student {
    public DemoStudentWithoutSection() {
        super("S-MISSING", "Demo student without section", "demo", "BSIT", 2, "Legacy section");
    }
    @Override public String getSection() { return " "; }
}
