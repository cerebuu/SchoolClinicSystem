package clinic.app.ui.common.popup;
import clinic.app.Theme;
import clinic.app.ui.common.AsyncLoader;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
public final class FormPopup {
    private FormPopup() {
    }
    public static void show(Component owner, String title, List<FormField> fields, Consumer<Map<String, String>> submit, Runnable saved) {
        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(owner), title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel form=new JPanel(new GridBagLayout());
        form.setBorder(Theme.padding());
        Map<String, JComponent> inputs=new LinkedHashMap<>();
        int row=0;
        for(FormField f:fields) {
            GridBagConstraints g=new GridBagConstraints();
            g.gridy=row++;
            g.gridx=0;
            g.anchor=GridBagConstraints.WEST;
            g.insets=Theme.fieldInsets();
            JLabel label=new JLabel(f.label()+(f.required()?" *":""));
            form.add(label, g);
            JComponent input=f.choices()!=null?new JComboBox<>(f.choices()):f.secret()?new JPasswordField(24):new JTextField(f.initial(), 24);
            if(input instanceof JComboBox<?> c)c.setSelectedItem(f.initial());
            input.getAccessibleContext().setAccessibleName(f.label());
            label.setLabelFor(input);
            Theme.field(input);
            g.gridx=1;
            g.weightx=1;
            g.fill=GridBagConstraints.HORIZONTAL;
            form.add(input, g);
            inputs.put(f.label(), input);
        }
        JLabel error=new JLabel(" ");
        error.setForeground(Theme.DANGER);
        JButton save=Theme.button("Save", true), cancel=Theme.button("Cancel", false);
        JPanel bottom=new JPanel(new BorderLayout());
        JPanel buttons=new JPanel();
        buttons.add(cancel);
        buttons.add(save);
        bottom.add(error, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);
        save.addActionListener(e-> {
            Map<String, String> values=new LinkedHashMap<>();
            for(FormField f:fields) {
                JComponent c=inputs.get(f.label());
                String v;
                if(c instanceof JPasswordField pw) {
                    char[] chars=pw.getPassword();
                    v=new String(chars);
                    Arrays.fill(chars, '\0');
                }
                else v=c instanceof JTextField t?t.getText().trim():Objects.toString(((JComboBox<?>)c).getSelectedItem(), "");
                if(f.required()&&v.isBlank()) {
                    error.setText(f.label()+" is required.");
                    c.requestFocusInWindow();
                    return;
                }
                values.put(f.label(), v);
            }
            save.setEnabled(false);
            cancel.setEnabled(false);
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            error.setText("Saving...");
            AsyncLoader.load(()-> {
                submit.accept(values);
                return true;
            }, ok-> {
                values.clear();
                inputs.values().forEach(c-> {
                    if(c instanceof JPasswordField p)p.setText("");
                });
                dialog.dispose();
                saved.run();
            }, failure-> {
                values.clear();
                save.setEnabled(true);
                cancel.setEnabled(true);
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                error.setText("Please check your input and try again.");
                MessagePopup.error(dialog, failure);
            });
        });
        cancel.addActionListener(e->dialog.dispose());
        dialog.add(new JScrollPane(form), BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(save);
        dialog.pack();
        dialog.setMinimumSize(dialog.getSize());
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
