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
        String message=error instanceof java.time.format.DateTimeParseException?"Enter a valid date in YYYY-MM-DD format.":error instanceof NumberFormatException?"Enter a valid whole number.":error instanceof IllegalArgumentException||error instanceof IllegalStateException||error instanceof SecurityException?error.getMessage():"The operation could not be completed. Please retry.";
        JOptionPane.showMessageDialog(owner, message, "Unable to complete", JOptionPane.ERROR_MESSAGE);
    }
    public static void warning(Component owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }
    public static boolean confirm(Component owner, String message) {
        return JOptionPane.showConfirmDialog(owner, message, "Confirm", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)==JOptionPane.OK_OPTION;
    }
}
