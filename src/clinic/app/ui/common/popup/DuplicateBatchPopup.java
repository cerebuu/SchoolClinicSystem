package clinic.app.ui.common.popup;

import clinic.app.Theme;
import clinic.model.Medicine;
import java.awt.*;
import javax.swing.*;

/** Shared-dialog decision for an incoming batch that matches unexpired stock. */
public final class DuplicateBatchPopup {
    public enum Choice { MERGE,SEPARATE,CANCEL }
    public enum EmptyChoice { REUSE,CREATE_NEW,CANCEL }
    private DuplicateBatchPopup() {}
    public static Choice show(Component owner,Medicine old,int count,int quantity,String remarks) {
        JDialog dialog=PopupDialog.create(owner,"Matching medicine batch");
        JPanel body=new JPanel();body.setLayout(new BoxLayout(body,BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_4,Theme.SPACE_6,Theme.SPACE_4,Theme.SPACE_6));
        body.add(section("Existing batch",old.getName()+" - quantity "+old.getQuantity()+"\nExpiry: "+old.getExpirationDate()
            +"   Batch date: "+old.getBatchDate()+"\nRemarks: "+shown(old.getRemarks())
            +(count>1?"\nMatching batches: "+count:"")));
        body.add(Box.createVerticalStrut(Theme.SPACE_3));
        body.add(section("New entry","Quantity: "+quantity+"\nRemarks: "+shown(remarks)));
        body.add(Box.createVerticalStrut(Theme.SPACE_3));
        body.add(section("If merged","Total quantity: "+old.getQuantity()+" + "+quantity+" = "+Math.addExact(old.getQuantity(),quantity)));
        Choice[] result={Choice.CANCEL};
        JPanel buttons=new JPanel();
        JButton merge=Theme.button("Merge quantities",true),separate=Theme.button("Keep separate batch",false),cancel=Theme.button("Cancel",false);
        merge.addActionListener(e->{result[0]=Choice.MERGE;dialog.dispose();});
        separate.addActionListener(e->{result[0]=Choice.SEPARATE;dialog.dispose();});
        cancel.addActionListener(e->dialog.dispose());
        buttons.add(cancel);buttons.add(separate);buttons.add(merge);
        dialog.add(body,BorderLayout.CENTER);dialog.add(buttons,BorderLayout.SOUTH);
        dialog.setMinimumSize(new Dimension(460,280));PopupDialog.show(owner,dialog);
        return result[0];
    }
    public static EmptyChoice reuseEmpty(Component owner,Medicine old,int quantity,java.time.LocalDate expiry,String remarks) {
        JDialog dialog=PopupDialog.create(owner,"Reuse empty batch?");
        JTextArea text=new JTextArea("Old: 0, expiry "+old.getExpirationDate()+", batch date "+old.getBatchDate()+".\n"
            +"New: "+quantity+", expiry "+expiry+", batch date today.\nNew remarks: "+shown(remarks));
        text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setFont(Theme.font(Theme.TYPE_BODY,Font.PLAIN));
        Theme.surface(text);
        JPanel body=new JPanel(new BorderLayout());body.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_4,Theme.SPACE_6,Theme.SPACE_4,Theme.SPACE_6));body.add(text);
        EmptyChoice[] result={EmptyChoice.CANCEL};
        JButton reuse=Theme.button("Reuse empty batch",true),create=Theme.button("Create new batch",false),cancel=Theme.button("Cancel",false);
        reuse.addActionListener(e->{result[0]=EmptyChoice.REUSE;dialog.dispose();});
        create.addActionListener(e->{result[0]=EmptyChoice.CREATE_NEW;dialog.dispose();});
        cancel.addActionListener(e->dialog.dispose());
        JPanel buttons=new JPanel();buttons.add(cancel);buttons.add(create);buttons.add(reuse);
        dialog.add(body,BorderLayout.CENTER);dialog.add(buttons,BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(reuse);dialog.setMinimumSize(new Dimension(500,220));PopupDialog.show(owner,dialog);
        return result[0];
    }
    public static boolean confirmGroup(Component owner,String message) {
        JDialog dialog=PopupDialog.create(owner,"Merge duplicate batches");
        JPanel content=new JPanel(new BorderLayout(0,Theme.SPACE_3));
        content.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_4,Theme.SPACE_6,Theme.SPACE_4,Theme.SPACE_6));
        JTextArea details=new JTextArea(message);details.setEditable(false);details.setLineWrap(true);details.setWrapStyleWord(true);
        details.setFont(Theme.font(Theme.TYPE_BODY,Font.PLAIN));details.setBackground(Theme.WHITE);
        Theme.surface(details);content.add(details,BorderLayout.CENTER);
        boolean[] confirmed={false};
        JButton merge=Theme.button("Merge this group",true),skip=Theme.button("Skip",false);
        merge.addActionListener(e->{confirmed[0]=true;dialog.dispose();});skip.addActionListener(e->dialog.dispose());
        JPanel buttons=new JPanel();buttons.add(skip);buttons.add(merge);
        dialog.add(content,BorderLayout.CENTER);dialog.add(buttons,BorderLayout.SOUTH);
        dialog.setMinimumSize(new Dimension(500,330));PopupDialog.show(owner,dialog);
        return confirmed[0];
    }
    private static JPanel section(String title,String text) {
        JPanel panel=new JPanel(new BorderLayout(0,Theme.SPACE_1));Theme.surface(panel);
        JLabel heading=new JLabel(title);heading.setFont(Theme.font(Theme.TYPE_BODY,Font.BOLD));
        JTextArea detail=new JTextArea(text);detail.setEditable(false);detail.setLineWrap(true);detail.setWrapStyleWord(true);
        detail.setFont(Theme.font(Theme.TYPE_BODY,Font.PLAIN));detail.setBackground(Theme.WHITE);
        panel.add(heading,BorderLayout.NORTH);panel.add(detail,BorderLayout.CENTER);return panel;
    }
    private static String shown(String value) { return value==null||value.isBlank()?"(none)":value; }
}
