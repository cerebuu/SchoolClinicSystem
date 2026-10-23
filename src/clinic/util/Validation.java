package clinic.util;
public final class Validation {
    private Validation() {
    }
    public static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
        if (value.length()>500) throw new IllegalArgumentException(field + " is too long.");
        return value.trim();
    }
    public static int positive(int value) {
        if(value<1) throw new IllegalArgumentException("Quantity must be positive.");
        return value;
    }
}
