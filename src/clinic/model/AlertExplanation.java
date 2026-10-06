package clinic.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import clinic.util.DateUtil;

// This class is responsible for explaining why an alert fired and what to do next.
public final class AlertExplanation implements Formattable {

    public enum AlertType {
        OUTBREAK,
        RECURRING_VISIT,
        LOW_STOCK,
        EXPIRING
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    private final AlertType alertType;
    private final Severity severity;
    private final int score;
    private final String summary;
    private final List<String> evidence;
    private final LocalDateTime windowStart;
    private final LocalDateTime windowEnd;
    private final String recommendedAction;

    public AlertExplanation(AlertType alertType, Severity severity, int score,
                            String summary, List<String> evidence,
                            LocalDateTime windowStart, LocalDateTime windowEnd,
                            String recommendedAction) {
        if (alertType == null || severity == null || summary == null
                || summary.trim().isEmpty() || evidence == null
                || windowStart == null || windowEnd == null
                || recommendedAction == null || recommendedAction.trim().isEmpty()) {
            throw new IllegalArgumentException("All alert explanation fields are required.");
        }
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("Severity score must be between 0 and 100.");
        }
        this.alertType = alertType;
        this.severity = severity;
        this.score = score;
        this.summary = summary;
        this.evidence = Collections.unmodifiableList(new ArrayList<String>(evidence));
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.recommendedAction = recommendedAction;
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public Severity getSeverity() {
        return severity;
    }

    public int getScore() {
        return score;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getEvidence() {
        return evidence;
    }

    public LocalDateTime getWindowStart() {
        return windowStart;
    }

    public LocalDateTime getWindowEnd() {
        return windowEnd;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    @Override
    public String toReport() {
        return fullReport();
    }

    public String toReport(User viewer) {
        if (viewer == null) {
            throw new IllegalArgumentException("A viewer is required.");
        }
        if (User.ASSISTANT.equals(viewer.getRole())) {
            return "ALERT " + severity + " (" + score + "/100)\n"
                    + summary + "\n";
        }
        return fullReport();
    }

    private String fullReport() {
        StringBuilder report = new StringBuilder();
        report.append("ALERT TYPE: ").append(alertType).append("\n");
        report.append("SEVERITY: ").append(severity).append(" (")
                .append(score).append("/100)\n");
        report.append("SUMMARY: ").append(summary).append("\n");
        report.append("TIME WINDOW: ").append(DateUtil.format(windowStart))
                .append(" to ").append(DateUtil.format(windowEnd)).append("\n");
        report.append("EVIDENCE:\n");
        for (String line : evidence) {
            report.append("- ").append(line).append("\n");
        }
        report.append("RECOMMENDED ACTION: ").append(recommendedAction).append("\n");
        return report.toString();
    }
}
