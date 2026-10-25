package clinic.service;
import java.util.Set;
/** Low stock means at most ten usable units across all batches. */
public final class FeatureRules {
    public static final int OUTBREAK_DAYS=2, RECURRING_DAYS=14, VISIT_THRESHOLD=3;
    public static final int USAGE_DAYS=30, LOW_STOCK_THRESHOLD=10, EXPIRY_DAYS=30;
    // Controlled consultation choices; free text is never an alert label.
    public static final java.util.List<String> SYMPTOM_CHOICES=java.util.List.of("Fever", "Cough", "Headache", "Sore throat", "Abdominal pain", "Other");
    public static final Set<String> SYMPTOMS=SYMPTOM_CHOICES.stream()
        .map(s->s.toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private FeatureRules() {}
}
