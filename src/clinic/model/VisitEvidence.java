package clinic.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// This class is responsible for carrying privacy-safe visit evidence for an alert.
public final class VisitEvidence {

    private final String recordId;
    private final String section;
    private final String department;
    private final List<String> symptoms;
    private final LocalDateTime visitDateTime;

    public VisitEvidence(String recordId, String section, String department,
                         List<String> symptoms, LocalDateTime visitDateTime) {
        require(recordId, "record ID");
        require(section, "section");
        require(department, "department");
        if (symptoms == null || visitDateTime == null) {
            throw new IllegalArgumentException("Symptoms and visit date are required.");
        }
        this.recordId = recordId;
        this.section = section;
        this.department = department;
        this.symptoms = Collections.unmodifiableList(new ArrayList<String>(symptoms));
        this.visitDateTime = visitDateTime;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getSection() {
        return section;
    }

    public String getDepartment() {
        return department;
    }

    public List<String> getSymptoms() {
        return symptoms;
    }

    public LocalDateTime getVisitDateTime() {
        return visitDateTime;
    }

    private static void require(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required.");
        }
    }
}
