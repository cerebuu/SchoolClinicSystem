package clinic.model;
import clinic.util.Validation;
public class Employee extends Patient {
    private final String department, position;
    public Employee(String id, String name, String contact, String department, String position) {
        super(id, name, contact, EMPLOYEE);
        this.department=Validation.required(department, "Department");
        this.position=Validation.required(position, "Position");
    }
    public String getDepartment() {
        return department;
    }
    public String getPosition() {
        return position;
    }
}
