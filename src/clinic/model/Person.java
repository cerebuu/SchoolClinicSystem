package clinic.model;
import clinic.util.Validation;
public class Person {
    private final String id, name;
    private String contactInfo;
    public Person(String id, String name, String contactInfo) {
        this.id=Validation.required(id, "ID");
        this.name=Validation.required(name, "Name");
        setContactInfo(contactInfo);
    }
    public String getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public String getContactInfo() {
        return contactInfo;
    }
    public void setContactInfo(String value) {
        contactInfo=Validation.required(value, "Contact");
    }
}
