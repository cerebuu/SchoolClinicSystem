import clinic.model.MedicalRecord;
import java.time.LocalDateTime;
import java.util.List;

/** Historical timestamps are demo-only; production models and constructor signatures are unchanged. */
public final class DatedDemoVisit extends MedicalRecord {
    private final LocalDateTime visited;
    public DatedDemoVisit(String id, String patient, LocalDateTime visited, String reason) {
        super(id, patient, List.of("Headache"), reason, "Demo only", "", "N001");
        this.visited = visited;
    }
    @Override public LocalDateTime getVisitDateTime() { return visited; }
}
