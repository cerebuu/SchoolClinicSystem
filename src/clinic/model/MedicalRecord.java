package clinic.model;

// This class is responsible for one clinic visit: symptoms, concern, remarks, date and time.
//
// Attributes (private): recordId, patientId, reason, assessment, medicationGiven, staffId (String), symptoms (List<String>), visitDateTime (LocalDateTime).
// Constructor takes all attributes except the date, which is set with LocalDateTime.now().
// Getters only. Saved records are not edited.
// Change the class line to: public class MedicalRecord implements Formattable.
// Then write toReport() and use DateUtil.format(...) for the date.
// Symptoms come from a controlled list so PatternDetector can match them.
public class MedicalRecord {
}
