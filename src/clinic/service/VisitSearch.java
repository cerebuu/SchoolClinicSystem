package clinic.service;

import clinic.model.*;
import java.time.LocalDateTime;
import java.util.*;

/** Shared bounded visit query. Filters stored rows before selecting a page; no search values are logged. */
public final class VisitSearch {
    private VisitSearch() {}
    public static PageResult<MedicalRecord> page(List<MedicalRecord> records, Map<String, Patient> patients,
            int page, int size, String query, String staffId, LocalDateTime from, LocalDateTime to) {
        if (to == null || from != null && from.isAfter(to))
            throw new IllegalArgumentException("A valid visit date range is required.");
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<MedicalRecord> matches = records.stream().filter(visit -> {
            if (staffId != null && !staffId.equals(visit.getStaffId())) return false;
            LocalDateTime date = visit.getVisitDateTime();
            if (date == null || date.isAfter(to) || from != null && date.isBefore(from)) return false;
            Patient patient = patients.get(visit.getPatientId());
            return needle.isEmpty() || visit.getPatientId().toLowerCase(Locale.ROOT).contains(needle)
                || patient != null && patient.getName() != null
                    && patient.getName().toLowerCase(Locale.ROOT).contains(needle);
        }).toList();
        return PageResult.select(TimeOrder.newestFirst(matches, MedicalRecord::getVisitDateTime),
            page, size, ignored -> true);
    }
}
