package clinic.app.ui.dashboard;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/** Calendar boundaries are inclusive, using local clinic time; weeks begin Monday. */
public enum VisitPeriod {
    THIS_WEEK("This week"), TODAY("Today"), ALL("All");
    private final String label;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    VisitPeriod(String label) { this.label = label; }
    public String toString() { return label; }
    public LocalDateTime start(LocalDateTime now) {
        return switch (this) {
            case THIS_WEEK -> now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
            case TODAY -> now.toLocalDate().atStartOfDay();
            case ALL -> null;
        };
    }
    public String description(LocalDateTime now) {
        return switch (this) {
            case THIS_WEEK -> "Showing visits this week (" + DATE.format(start(now)) + " - "
                + DATE.format(start(now).plusDays(6)) + ")";
            case TODAY -> "Showing visits today (" + DATE.format(now) + ")";
            case ALL -> "Showing all visits up to now";
        };
    }
    public String emptyMessage(String query) {
        if (query.isBlank()) return "No visits yet. Click + New Consultation to add one"
            + (this == ALL ? "" : ". Switch to All for older visits.");
        String when = this == THIS_WEEK ? " this week" : this == TODAY ? " today" : "";
        String display = query.length() > 60 ? query.substring(0, 60) + "..." : query;
        return "No visit" + when + " for '" + display + "'" + (this == ALL ? "" : ". Switch to All to search older visits.");
    }
}
