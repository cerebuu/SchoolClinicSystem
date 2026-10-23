package clinic.app.ui.common.popup;
import clinic.app.Theme;
import javax.swing.*;
import java.awt.Component;
public final class MessagePopup {
    private MessagePopup() {
    }
    public static void info(Component owner, String message) {
        JTextArea text=new JTextArea(message, 16, 65);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(Theme.font(Theme.TYPE_BODY, java.awt.Font.PLAIN));
        text.setBackground(Theme.WHITE);
        text.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JOptionPane.showMessageDialog(owner, new JScrollPane(text), "School clinic", JOptionPane.INFORMATION_MESSAGE);
    }
    public static void error(Component owner, Throwable error) {
        JOptionPane.showMessageDialog(owner, errorText(error), "Unable to complete", JOptionPane.ERROR_MESSAGE);
    }
    public static String errorText(Throwable error) {
        if (error instanceof clinic.persistence.StorageException) return error.getMessage();
        if (error instanceof java.time.format.DateTimeParseException) return "Enter a valid date in YYYY-MM-DD format.";
        if (error instanceof NumberFormatException) return "Enter a valid whole number.";
        if (error instanceof ArithmeticException) return "The quantity is too large. Enter a smaller number.";
        if ((error instanceof IllegalArgumentException || error instanceof IllegalStateException
                || error instanceof SecurityException) && error.getMessage() != null) return error.getMessage();
        java.util.logging.Logger.getLogger(MessagePopup.class.getName()).log(
            java.util.logging.Level.WARNING, "Unexpected clinic operation failure", error);
        return "The operation could not be completed. Please retry.";
    }
    public static void warning(Component owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }
    public static boolean confirm(Component owner, String message) {
        return JOptionPane.showConfirmDialog(owner, message, "Confirm", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)==JOptionPane.OK_OPTION;
    }
}
