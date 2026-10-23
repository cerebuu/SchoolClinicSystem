package clinic.model;
import clinic.util.Validation;
public record Campus(String id, String name) {
    public Campus {
        id=Validation.required(id, "Campus ID");
        name=Validation.required(name, "Campus name");
    }
}
