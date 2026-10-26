package clinic.util;

import java.util.*;

/** Normalizes staff-entered allergy lists without changing their first spelling or order. */
public final class AllergyParser {
    private AllergyParser() {}
    public static List<String> parse(String input) {
        if (input == null || input.isBlank()) return List.of();
        Map<String,String> unique = new LinkedHashMap<>();
        for (String part : input.split("[,;\\r\\n]+")) {
            String item = part.replaceAll("(?U)\\s+", " ").trim();
            if (item.isEmpty()) continue;
            if (item.length() > 50) throw new IllegalArgumentException("Each allergy must be at most 50 characters.");
            unique.putIfAbsent(item.toLowerCase(Locale.ROOT), item);
            if (unique.size() > 20) throw new IllegalArgumentException("Enter at most 20 allergies.");
        }
        return List.copyOf(unique.values());
    }
}
