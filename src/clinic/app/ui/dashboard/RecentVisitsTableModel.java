package clinic.app.ui.dashboard;

import clinic.app.ui.common.PagedTableModel;
import clinic.util.DateUtil;
import clinic.util.PatientDisplay;

public final class RecentVisitsTableModel extends PagedTableModel<RecentVisitRow> {
    public RecentVisitsTableModel() {
        super("Patient", "ID Number", "Type", "Section / Position", "Purpose", "Date");
    }

    protected Object value(RecentVisitRow row, int column) {
        return switch (column) {
            case 0 -> row.patient() == null ? "-" : row.patient().getName();
            case 1 -> row.visit().getPatientId();
            case 2 -> PatientDisplay.getType(row.patient());
            case 3 -> PatientDisplay.getSectionOrPosition(row.patient());
            case 4 -> row.visit().getReason();
            case 5 -> DateUtil.format(row.visit().getVisitDateTime());
            default -> "";
        };
    }
}
