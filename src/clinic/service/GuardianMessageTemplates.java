package clinic.service;

import java.time.LocalDateTime;

import clinic.util.DateUtil;

// This class is responsible for creating safe, ready-to-send guardian messages.
public final class GuardianMessageTemplates {

    public enum MessageType {
        CLINIC_VISIT,
        MEDICINE_GIVEN,
        SENT_HOME,
        FOLLOW_UP_NEEDED,
        EMERGENCY
    }

    private GuardianMessageTemplates() {
    }

    public static String create(MessageType type, String patientName, String reason,
                                String medicineName, LocalDateTime dateTime,
                                String clinicContact) {
        require(type, "message type");
        require(patientName, "patient name");
        require(reason, "reason");
        require(dateTime, "date and time");
        require(clinicContact, "clinic contact");
        if (type == MessageType.MEDICINE_GIVEN) {
            require(medicineName, "medicine name");
        }

        String date = DateUtil.format(dateTime);
        if (type == MessageType.CLINIC_VISIT) {
            return "School Clinic: " + patientName + " visited the clinic on "
                    + date + " for " + reason + ". Contact: " + clinicContact;
        }
        if (type == MessageType.MEDICINE_GIVEN) {
            return "School Clinic: " + patientName + " was given " + medicineName
                    + " on " + date + " for " + reason + ". Contact: " + clinicContact;
        }
        if (type == MessageType.SENT_HOME) {
            return "School Clinic: " + patientName + " was sent home on "
                    + date + " because of " + reason + ". Contact: " + clinicContact;
        }
        if (type == MessageType.FOLLOW_UP_NEEDED) {
            return "School Clinic: " + patientName + " needs follow-up after "
                    + reason + " on " + date + ". Contact: " + clinicContact;
        }
        return "URGENT: Please contact the school clinic about " + patientName
                + " immediately regarding " + reason + ". Contact: " + clinicContact;
    }

    public static String clinicVisit(String patientName, String reason,
                                     LocalDateTime dateTime, String clinicContact) {
        return create(MessageType.CLINIC_VISIT, patientName, reason, null,
                dateTime, clinicContact);
    }

    public static String medicineGiven(String patientName, String reason,
                                       String medicineName, LocalDateTime dateTime,
                                       String clinicContact) {
        return create(MessageType.MEDICINE_GIVEN, patientName, reason, medicineName,
                dateTime, clinicContact);
    }

    public static String sentHome(String patientName, String reason,
                                  LocalDateTime dateTime, String clinicContact) {
        return create(MessageType.SENT_HOME, patientName, reason, null,
                dateTime, clinicContact);
    }

    public static String followUpNeeded(String patientName, String reason,
                                        LocalDateTime dateTime, String clinicContact) {
        return create(MessageType.FOLLOW_UP_NEEDED, patientName, reason, null,
                dateTime, clinicContact);
    }

    public static String emergency(String patientName, String reason,
                                   LocalDateTime dateTime, String clinicContact) {
        return create(MessageType.EMERGENCY, patientName, reason, null,
                dateTime, clinicContact);
    }

    private static void require(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        if (value instanceof String && ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required.");
        }
    }
}
