package clinic.app.ui.common.popup;
import clinic.app.Theme;
import clinic.app.ui.common.CopyableIdCell;
import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Map;
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
    public static void infoWithCopies(Component owner,String message,Map<String,String> ids) {
        JPanel content=new JPanel(new BorderLayout(Theme.GAP,Theme.GAP));content.setBackground(Theme.WHITE);
        JPanel rows=new JPanel();rows.setOpaque(false);rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));
        ids.forEach((label,id)->{
            if(id==null||id.isBlank())return;
            JLabel title=new JLabel(label);title.setFont(Theme.font(Theme.TYPE_CAPTION,java.awt.Font.BOLD));
            JTextField value=new JTextField(id,30);value.setName("id:"+label);value.setEditable(false);Theme.field(value);
            JButton copy=Theme.button("Copy",false);copy.setToolTipText("Copy "+label);
            copy.setName("copy:"+label);
            copy.getAccessibleContext().setAccessibleName("Copy "+label);
            value.getAccessibleContext().setAccessibleName(label);
            copy.addActionListener(event->{
                CopyableIdCell.copyToClipboard(id);copy.setText("Copied");
                Timer feedback=new Timer(1500,t->{copy.setText("Copy");((Timer)t.getSource()).stop();});
                feedback.setRepeats(false);feedback.start();
            });
            JPanel row=new JPanel(new BorderLayout(Theme.SPACE_2,0));row.setOpaque(false);
            row.add(title,BorderLayout.WEST);row.add(value,BorderLayout.CENTER);row.add(copy,BorderLayout.EAST);
            rows.add(row);rows.add(Box.createVerticalStrut(Theme.SPACE_2));
        });
        JTextArea text=new JTextArea(message,12,65);text.setEditable(false);text.setLineWrap(true);
        text.setWrapStyleWord(true);text.setFont(Theme.font(Theme.TYPE_BODY,java.awt.Font.PLAIN));
        text.setBackground(Theme.WHITE);text.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_1,
            Theme.SPACE_1,Theme.SPACE_1,Theme.SPACE_1));
        content.add(rows,BorderLayout.NORTH);content.add(new JScrollPane(text),BorderLayout.CENTER);
        JOptionPane.showMessageDialog(owner,content,"School clinic",JOptionPane.INFORMATION_MESSAGE);
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
            java.util.logging.Level.WARNING, "Unexpected clinic operation failure ({0})", error.getClass().getSimpleName());
        return "The operation could not be completed. Please retry.";
    }
    public static void warning(Component owner, String message) {
        JOptionPane.showMessageDialog(owner, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }
    public static boolean confirm(Component owner, String message) {
        return JOptionPane.showConfirmDialog(owner, message, "Confirm", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)==JOptionPane.OK_OPTION;
    }
}
