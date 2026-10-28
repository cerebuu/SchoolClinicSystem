package clinic.app.ui.common.popup;

public interface FormValueComponent {
    String READY_PROPERTY="formReady";
    String formValue();
    default boolean isFormReady() { return true; }
    /** Inline validation for compound fields that render their own row-level errors. */
    default boolean validateForm() { return true; }
}
