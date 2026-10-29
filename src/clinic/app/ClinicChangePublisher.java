package clinic.app;
import static clinic.app.DataTopic.*;

/** Only called after a secured mutation returns successfully. Each committed dispense row emits. */
public final class ClinicChangePublisher {
    private ClinicChangePublisher() {}
    public static void changed(DataChangeBus bus,String method) {
        switch(method) {
            case "addMedicine","restockMedicine" -> bus.publish(INVENTORY,ALERTS);
            case "dispenseMedicine" -> bus.publish(INVENTORY,TRANSACTIONS,ALERTS);
            case "recordVisit" -> bus.publish(VISITS,ALERTS);
            case "recordVisitAndAdminister","recordVisitAndAdministerWithReason" -> bus.publish(VISITS,TRANSACTIONS,INVENTORY,ALERTS);
            case "registerPatient","updatePatient" -> bus.publish(PATIENTS,ALERTS);
            case "addAllergy" -> bus.publish(PATIENTS);
            case "createGuardianNotification","createGuardianNotificationWithRecipient",
                "createEmergencyNotification","updateNotificationStatus",
                "beginNotificationAttempt","completeNotificationAttempt" -> bus.publish(NOTIFICATIONS,ALERTS);
            default -> { }
        }
    }
}
