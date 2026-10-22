import clinic.model.Employee;

/** Demo-only legacy row: models still require positions for newly registered employees. */
public final class DemoEmployeeWithoutPosition extends Employee {
    public DemoEmployeeWithoutPosition() {
        super("E-MISSING", "Demo employee without position", "demo", "Administration", "Legacy position");
    }
    @Override public String getPosition() { return null; }
}
