package clinic.model;
import clinic.util.Validation;
public class Student extends Patient {
    private final String course, section;
    private final int yearLevel;
    public Student(String id, String name, String contact, String course, int year, String section) {
        super(id, name, contact, STUDENT);
        this.course=Validation.required(course, "Course");
        this.section=Validation.required(section, "Section");
        yearLevel=Validation.positive(year);
    }
    public String getCourse() {
        return course;
    }
    public String getSection() {
        return section;
    }
    public int getYearLevel() {
        return yearLevel;
    }
}
