package clinic.app.ui.common.popup;
public record FormField(String label, String initial, boolean required, boolean secret, String[] choices) {
    public static FormField text(String label) {
        return new FormField(label, "", true, false, null);
    }
    public static FormField optional(String label) {
        return new FormField(label, "", false, false, null);
    }
    public static FormField value(String label, String value) {
        return new FormField(label, value, true, false, null);
    }
    public static FormField password() {
        return new FormField("Password", "", true, true, null);
    }
    public static FormField choice(String label, String... choices) {
        return new FormField(label, choices[0], true, false, choices);
    }
}
