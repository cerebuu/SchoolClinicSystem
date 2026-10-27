package clinic.model;

import clinic.util.Validation;
import java.util.Locale;

public record MedicineProduct(String name, String strength, String formulation) {
    public MedicineProduct {
        name = Validation.required(name, "Medicine");
        strength = normalize(strength);
        formulation = normalize(formulation);
        if (strength.length() > 100 || formulation.length() > 100) {
            throw new IllegalArgumentException("Medicine strength and formulation must be at most 100 characters.");
        }
    }

    public String key() {
        return normalized(name) + "\u001f" + normalized(strength) + "\u001f" + normalized(formulation);
    }

    public String displayName() {
        StringBuilder label = new StringBuilder(name);
        if (!strength.isBlank()) label.append(" ").append(strength);
        if (!formulation.isBlank()) label.append(" (").append(formulation).append(")");
        return label.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String normalized(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
