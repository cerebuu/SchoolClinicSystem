package clinic.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import clinic.model.Alert;
import clinic.model.AlertExplanation;
import clinic.model.StockEvidence;
import clinic.model.VisitEvidence;

// This class is responsible for turning alert evidence into explainable results.
public class AlertExplainer {

    public AlertExplanation explain(Alert alert, AlertExplanation.AlertType type,
                                    List<VisitEvidence> visits, StockEvidence stock,
                                    int threshold, LocalDateTime windowStart,
                                    LocalDateTime windowEnd) {
        if (alert == null || type == null || visits == null
                || windowStart == null || windowEnd == null || threshold < 1
                || windowEnd.isBefore(windowStart)) {
            throw new IllegalArgumentException("Valid alert inputs are required.");
        }
        List<VisitEvidence> safeVisits = new ArrayList<VisitEvidence>(visits);
        if (type == AlertExplanation.AlertType.OUTBREAK) {
            return outbreak(safeVisits, threshold, windowStart, windowEnd);
        }
        if (type == AlertExplanation.AlertType.RECURRING_VISIT) {
            return recurring(safeVisits, threshold, windowStart, windowEnd);
        }
        if (stock == null) {
            throw new IllegalArgumentException("Stock evidence is required.");
        }
        if (type == AlertExplanation.AlertType.LOW_STOCK) {
            return lowStock(stock, threshold, windowStart, windowEnd);
        }
        if (type == AlertExplanation.AlertType.EXPIRING) {
            return expiring(stock, threshold, windowStart, windowEnd);
        }
        throw new IllegalArgumentException("Unknown alert type.");
    }

    private AlertExplanation outbreak(List<VisitEvidence> visits, int threshold,
                                      LocalDateTime start, LocalDateTime end) {
        int count = visits.size();
        int score = outbreakScore(count, threshold, start, end);
        List<String> evidence = visitEvidence(visits);
        String symptoms = symptomCounts(visits);
        String summary = "Outbreak rule: " + count + " visits crossed threshold "
                + threshold + " in the time window; symptoms: " + symptoms + ".";
        return explanation(AlertExplanation.AlertType.OUTBREAK, score, summary, evidence,
                start, end, "Review the listed records and begin outbreak response.");
    }

    private AlertExplanation recurring(List<VisitEvidence> visits, int threshold,
                                      LocalDateTime start, LocalDateTime end) {
        int count = visits.size();
        int score = Math.min(100, 30 + Math.max(0, count - threshold + 1) * 20);
        List<String> evidence = visitEvidence(visits);
        String symptoms = symptomCounts(visits);
        String summary = "Recurring-visit rule: " + count + " visits crossed threshold "
                + threshold + " in the time window; symptoms: " + symptoms + ".";
        return explanation(AlertExplanation.AlertType.RECURRING_VISIT, score, summary,
                evidence, start, end, "Review the visit pattern and schedule follow-up.");
    }

    private AlertExplanation lowStock(StockEvidence stock, int threshold,
                                      LocalDateTime start, LocalDateTime end) {
        int gap = Math.max(0, threshold - stock.getQuantity());
        int score = Math.min(100, 50 + gap * 10);
        List<String> evidence = Collections.singletonList(
                "Medicine " + stock.getMedicineName() + " quantity=" + stock.getQuantity()
                        + "; count=" + stock.getQuantity() + "; threshold=" + threshold
                        + "; shortage=" + gap + "; symptoms=not applicable.");
        String summary = "Low-stock rule: quantity " + stock.getQuantity()
                + " is at or below threshold " + threshold
                + "; symptoms: not applicable.";
        return explanation(AlertExplanation.AlertType.LOW_STOCK, score, summary, evidence,
                start, end, "Restock the medicine before the next shift.");
    }

    private AlertExplanation expiring(StockEvidence stock, int threshold,
                                      LocalDateTime start, LocalDateTime end) {
        long days = Duration.between(LocalDate.now().atStartOfDay(),
                stock.getExpiryDate().atStartOfDay()).toDays();
        int score = Math.min(100, Math.max(0, 100 - (int) Math.max(0, days) * 10));
        List<String> evidence = Collections.singletonList(
                "Medicine " + stock.getMedicineName() + " quantity=" + stock.getQuantity()
                        + "; count=" + stock.getQuantity() + "; expiry=" + stock.getExpiryDate()
                        + "; thresholdDays=" + threshold + "; symptoms=not applicable.");
        String summary = "Expiring rule: medicine expires in " + days
                + " days, within threshold " + threshold
                + " days; symptoms: not applicable.";
        return explanation(AlertExplanation.AlertType.EXPIRING, score, summary, evidence,
                start, end, "Review the batch and use or replace it according to policy.");
    }

    private AlertExplanation explanation(AlertExplanation.AlertType type, int score,
                                         String summary, List<String> evidence,
                                         LocalDateTime start, LocalDateTime end,
                                         String action) {
        AlertExplanation.Severity severity;
        if (score >= 80) {
            severity = AlertExplanation.Severity.CRITICAL;
        } else if (score >= 60) {
            severity = AlertExplanation.Severity.HIGH;
        } else if (score >= 30) {
            severity = AlertExplanation.Severity.MEDIUM;
        } else {
            severity = AlertExplanation.Severity.LOW;
        }
        return new AlertExplanation(type, severity, score, summary, evidence,
                start, end, action);
    }

    private int outbreakScore(int count, int threshold, LocalDateTime start,
                              LocalDateTime end) {
        long minutes = Math.max(1, Duration.between(start, end).toMinutes());
        int excessScore = Math.max(0, count - threshold + 1) * 15;
        int timeScore = (int) Math.max(0, 40 - Math.min(40, minutes / 15));
        return Math.min(100, 20 + excessScore + timeScore);
    }

    private List<String> visitEvidence(List<VisitEvidence> visits) {
        List<String> evidence = new ArrayList<String>();
        if (visits.isEmpty()) {
            evidence.add("No visit evidence found; count=0.");
            return evidence;
        }
        for (VisitEvidence visit : visits) {
            evidence.add("Record " + visit.getRecordId() + ", section="
                    + visit.getSection() + ", department=" + visit.getDepartment()
                    + ", symptomCount=" + visit.getSymptoms().size() + ".");
        }
        return evidence;
    }

    private String symptomCounts(List<VisitEvidence> visits) {
        List<String> symptoms = new ArrayList<String>();
        for (VisitEvidence visit : visits) {
            for (String symptom : visit.getSymptoms()) {
                if (symptom != null && !symptom.trim().isEmpty()
                        && !symptoms.contains(symptom)) {
                    symptoms.add(symptom);
                }
            }
        }
        return symptoms.isEmpty() ? "none recorded" : join(symptoms);
    }

    private String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(values.get(i));
        }
        return result.toString();
    }
}
