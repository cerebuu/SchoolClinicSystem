package clinic.util;

import clinic.model.Employee;
import clinic.model.Patient;
import clinic.model.Student;
import java.util.Locale;

/** Shared patient-type presentation and search rules, independent of Swing. */
public final class PatientDisplay {
    private PatientDisplay() {}

    public static String getSectionOrPosition(Patient patient) {
        if (patient instanceof Student student) return present(student.getSection());
        if (patient instanceof Employee employee) return present(employee.getPosition());
        return "-";
    }

    public static String getType(Patient patient) {
        if (patient instanceof Student) return "Student";
        if (patient instanceof Employee) return "Employee";
        return "-";
    }

    public static String getEmployeeDepartment(Patient patient) {
        return patient instanceof Employee employee ? present(employee.getDepartment()) : "-";
    }

    public static boolean matchesSearch(Patient patient, String query) {
        if (patient == null) return false;
        if (query == null || query.isBlank()) return true;
        String search = query.strip().toLowerCase(Locale.ROOT);
        return contains(patient.getId(), search) || contains(patient.getName(), search)
            || contains(getSectionOrPosition(patient), search)
            || contains(getEmployeeDepartment(patient), search);
    }

    private static boolean contains(String value, String search) {
        String text = present(value);
        return !text.equals("-") && text.toLowerCase(Locale.ROOT).contains(search);
    }

    private static String present(String value) {
        return value == null || value.isBlank() || value.strip().equalsIgnoreCase("null")
            ? "-" : value.strip();
    }
}
