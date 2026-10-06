package clinic.service;

import java.time.LocalDateTime;

import clinic.model.Formattable;
import clinic.model.MedicalRecord;
import clinic.model.MedicineTransaction;
import clinic.model.User;
import clinic.util.DateUtil;

// This class is responsible for turning records into readable reports.
public class RecordFormatter {

    private static final String LINE = "========================================";

    /** Formats one clinic visit. Works once MedicalRecord implements Formattable. */
    public String formatMedicalRecord(MedicalRecord medicalRecord) {
        return generateReport(asFormattable(medicalRecord, "MedicalRecord"));
    }

    /** Formats one medicine release. Works once MedicineTransaction implements Formattable. */
    public String formatTransactionRecord(MedicineTransaction transaction) {
        return generateReport(asFormattable(transaction, "MedicineTransaction"));
    }

    /** Works for any Formattable (polymorphism): header + item.toReport() + footer. */
    public String generateReport(Formattable item) {
        if (item == null) {
            throw new IllegalArgumentException("There is nothing to format.");
        }
        return header(null) + item.toReport() + "\n" + footer();
    }

    /**
     * Same report, but only for users allowed to see reports.
     * The header shows who the report was prepared for.
     */
    public String generateReport(Formattable item, User viewer) {
        if (item == null) {
            throw new IllegalArgumentException("There is nothing to format.");
        }
        if (viewer == null) {
            throw new IllegalArgumentException("A viewer is required.");
        }
        if (!viewer.canAccess(User.REPORTS) && !viewer.canAccess(User.VIEW_REPORTS)) {
            throw new SecurityException(
                viewer.getName() + " (" + viewer.getRole() + ") is not allowed to view reports.");
        }
        return header(viewer) + item.toReport() + "\n" + footer();
    }

    private String header(User viewer) {
        StringBuilder sb = new StringBuilder();
        sb.append(LINE).append("\n");
        sb.append("SCHOOL CLINIC REPORT\n");
        sb.append("Generated: ").append(DateUtil.format(LocalDateTime.now())).append("\n");
        if (viewer != null) {
            sb.append("Prepared for: ").append(viewer.getName())
              .append(" (").append(viewer.getRole()).append(")\n");
        }
        sb.append(LINE).append("\n");
        return sb.toString();
    }

    private String footer() {
        return LINE + "\nEnd of report\n" + LINE;
    }

    // TODO (after CJ merges #28 and #27): MedicalRecord and MedicineTransaction should
    // implement Formattable, and this check can be replaced with a direct call.
    private Formattable asFormattable(Object item, String typeName) {
        if (item == null) {
            throw new IllegalArgumentException("There is no " + typeName + " to format.");
        }
        if (!(item instanceof Formattable)) {
            throw new IllegalStateException(typeName + " does not implement Formattable yet.");
        }
        return (Formattable) item;
    }
}
