package clinic.app.ui.common.popup;
public record FormField(String label, String initial, boolean required, boolean secret, String[] choices,
        int rows, int maxLength, java.util.function.Function<String,String> validator, boolean allergyInput) {
    public FormField(String label, String initial, boolean required, boolean secret, String[] choices,
            int rows, int maxLength, java.util.function.Function<String,String> validator) {
        this(label, initial, required, secret, choices, rows, maxLength, validator, false);
    }
    public static FormField allergies() {
        return new FormField("Allergies", "", false, false, null, 0, 0, value -> {
            try { clinic.util.AllergyParser.parse(value); return null; }
            catch (IllegalArgumentException error) { return error.getMessage(); }
        }, true);
    }
    public FormField(String label, String initial, boolean required, boolean secret, String[] choices) {
        this(label, initial, required, secret, choices, 0, 0, value -> null);
    }
    public static FormField remarks() {
        return remarks("");
    }
    public static FormField remarks(String initial) {
        return new FormField("Remarks", initial==null?"":initial, false, false, null, 3, 200,
            value -> value.length() > 200 ? "Remarks must be at most 200 characters." : null);
    }
    public static FormField positiveQuantity() {
        return new FormField("Quantity", "", true, false, null, 0, 0, value -> {
            try { if (value.matches("[0-9]+") && Integer.parseInt(value) >= 1) return null; }
            catch (NumberFormatException ignored) { }
            return "Quantity must be a whole number of at least 1.";
        });
    }
    public static FormField futureExpiry() {
        return futureExpiry("");
    }
    public static FormField futureExpiry(String initial) {
        return new FormField("Expiry (YYYY-MM-DD)", initial==null?"":initial, true, false, null, 0, 0, value -> {
            try {
                if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                        && java.time.LocalDate.parse(value).isAfter(java.time.LocalDate.now())) return null;
            } catch (java.time.DateTimeException ignored) { }
            return "Expiry must be a valid future date (YYYY-MM-DD).";
        });
    }
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
