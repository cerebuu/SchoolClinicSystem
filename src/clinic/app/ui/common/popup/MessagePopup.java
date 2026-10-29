package clinic.app.ui.common.popup;

import clinic.app.Theme;
import clinic.app.ui.common.ClipboardSupport;
import clinic.app.ui.common.CopyButton;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

public final class MessagePopup {
    public static final String CONSULTATION_RECORD_TITLE="Consultation record";

    private MessagePopup() {}

    public static void info(Component owner,String message) {
        JTextArea text=new JTextArea(message,16,65);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(Theme.font(Theme.TYPE_BODY,java.awt.Font.PLAIN));
        text.setBackground(Theme.WHITE);
        text.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        JOptionPane.showMessageDialog(owner,new JScrollPane(text),"School clinic",JOptionPane.INFORMATION_MESSAGE);
    }

    public static void infoWithCopies(Component owner,String message,Map<String,String> ids) {
        infoWithCopies(owner,"School clinic",message,ids,false);
    }

    public static void infoWithCopies(Component owner,String title,String message,Map<String,String> ids,
            boolean includeCopyAll) {
        infoWithCopies(owner,title,message,ids,"Copy report",includeCopyAll);
    }

    public static void infoWithCopies(Component owner,String title,String message,Map<String,String> ids,
            String copyLabel,boolean includeCopyAll) {
        JDialog dialog=PopupDialog.create(owner,title);
        JPanel root=new JPanel(new BorderLayout(Theme.GAP,Theme.GAP));
        root.setBackground(Theme.WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_4,Theme.SPACE_6,Theme.SPACE_2,Theme.SPACE_6));
        root.add(detailsContent(message,ids,ClipboardSupport::copyText),BorderLayout.CENTER);
        root.add(detailsActions(copyLabel,message,ids,includeCopyAll,
            ClipboardSupport::copyText,dialog::dispose),BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.setMinimumSize(new Dimension(560,380));
        dialog.getRootPane().setDefaultButton(requireCloseButton(root));
        dialog.getRootPane().registerKeyboardAction(event->dialog.dispose(),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
        PopupDialog.show(owner,dialog);
    }

    public static void infoWithCopy(Component owner,String title,String message) {
        JDialog dialog=PopupDialog.create(owner,title);
        JPanel root=new JPanel(new BorderLayout(Theme.GAP,Theme.GAP));
        root.setBackground(Theme.WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_4,Theme.SPACE_6,Theme.SPACE_2,Theme.SPACE_6));
        root.add(detailsContent(message,Map.of(),ClipboardSupport::copyText),BorderLayout.CENTER);
        root.add(detailsActions("Copy",message,Map.of(),false,ClipboardSupport::copyText,dialog::dispose),
            BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.setMinimumSize(new Dimension(560,380));
        dialog.getRootPane().setDefaultButton(requireCloseButton(root));
        dialog.getRootPane().registerKeyboardAction(event->dialog.dispose(),
            KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
        PopupDialog.show(owner,dialog);
    }

    static JPanel detailsContent(String message,Map<String,String> ids,Consumer<String> copier) {
        JPanel content=new JPanel(new BorderLayout(Theme.GAP,Theme.GAP));
        content.setBackground(Theme.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(0,0,Theme.SPACE_2,0));
        JPanel rows=new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));
        Map<String,String> visibleIds=new LinkedHashMap<>();
        ids.forEach((label,id)->{
            if(id==null||id.isBlank())return;
            visibleIds.put(label,id);
            JLabel title=new JLabel(label);
            title.setFont(Theme.font(Theme.TYPE_CAPTION,java.awt.Font.BOLD));
            JTextField value=new JTextField(id,30);
            value.setName("id:"+label);
            value.setEditable(false);
            Theme.field(value);
            JButton copy=CopyButton.create("Copy","copy:"+label,id,copier);
            copy.setToolTipText("Copy "+label);
            value.getAccessibleContext().setAccessibleName(label);
            JPanel row=new JPanel(new BorderLayout(Theme.SPACE_2,0));
            row.setOpaque(false);
            row.add(title,BorderLayout.WEST);
            row.add(value,BorderLayout.CENTER);
            row.add(copy,BorderLayout.EAST);
            rows.add(row);
            rows.add(Box.createVerticalStrut(Theme.SPACE_2));
        });
        if(!visibleIds.isEmpty())content.add(rows,BorderLayout.NORTH);
        JTextArea text=new JTextArea(message,12,65);
        text.setName("report-text");
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(Theme.font(Theme.TYPE_BODY,java.awt.Font.PLAIN));
        text.setBackground(Theme.WHITE);
        text.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_1,Theme.SPACE_1,
            Theme.SPACE_1,Theme.SPACE_1));
        text.setCaretPosition(0);
        JScrollPane report=new JScrollPane(text,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        report.setName("report-scroll");
        report.setWheelScrollingEnabled(true);
        content.add(report,BorderLayout.CENTER);
        content.setPreferredSize(new Dimension(700,390));
        return content;
    }

    static JPanel detailsActions(String copyLabel,String message,Map<String,String> ids,
            boolean includeCopyAll,Consumer<String> copier,Runnable closeAction) {
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,Theme.SPACE_2,Theme.SPACE_2));
        actions.setOpaque(false);
        actions.add(CopyButton.create(copyLabel,"copy:report",message,copier));
        if(includeCopyAll)actions.add(CopyButton.create("Copy all","copy:all",copyAllText(ids,message),copier));
        JButton close=Theme.button("Close",true);
        close.setName("close:details");
        close.addActionListener(event->closeAction.run());
        actions.add(close);
        return actions;
    }

    private static JButton requireCloseButton(JComponent content) {
        JButton close=findCloseButton(content);
        if(close==null)throw new IllegalStateException("Details popup is missing its Close button.");
        return close;
    }

    private static JButton findCloseButton(JComponent content) {
        if(content instanceof JButton button&&"close:details".equals(button.getName()))return button;
        for(Component child:content.getComponents()) {
            if(child instanceof JButton button&&"close:details".equals(button.getName()))return button;
            if(child instanceof JComponent component) {
                JButton found=findCloseButton(component);
                if(found!=null)return found;
            }
        }
        return null;
    }

    private static String copyAllText(Map<String,String> ids,String message) {
        String header=ids.entrySet().stream().filter(entry->entry.getValue()!=null&&!entry.getValue().isBlank())
            .map(entry->entry.getKey()+": "+entry.getValue())
            .collect(java.util.stream.Collectors.joining("\n"));
        return header.isEmpty()?message:header+"\n\n"+message;
    }

    public static void error(Component owner,Throwable error) {
        JOptionPane.showMessageDialog(owner,errorText(error),"Unable to complete",JOptionPane.ERROR_MESSAGE);
    }

    public static String errorText(Throwable error) {
        if(error instanceof clinic.persistence.StorageException)return error.getMessage();
        if(error instanceof java.time.format.DateTimeParseException)return "Enter a valid date in YYYY-MM-DD format.";
        if(error instanceof NumberFormatException)return "Enter a valid whole number.";
        if(error instanceof ArithmeticException)return "The quantity is too large. Enter a smaller number.";
        if((error instanceof IllegalArgumentException||error instanceof IllegalStateException
                ||error instanceof SecurityException)&&error.getMessage()!=null)return error.getMessage();
        java.util.logging.Logger.getLogger(MessagePopup.class.getName()).log(
            java.util.logging.Level.WARNING,"Unexpected clinic operation failure ({0})",
            error.getClass().getSimpleName());
        return "The operation could not be completed. Please retry.";
    }

    public static void warning(Component owner,String message) {
        JOptionPane.showMessageDialog(owner,message,"Warning",JOptionPane.WARNING_MESSAGE);
    }

    public static boolean confirm(Component owner,String message) {
        return JOptionPane.showConfirmDialog(owner,message,"Confirm",JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE)==JOptionPane.OK_OPTION;
    }
}
