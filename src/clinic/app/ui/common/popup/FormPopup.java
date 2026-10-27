package clinic.app.ui.common.popup;
import clinic.app.Theme;
import clinic.app.ui.common.AsyncLoader;
import clinic.app.ui.common.Card;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
public final class FormPopup {
    private FormPopup() {
    }
    public static void show(Component owner, String title, List<FormField> fields, Consumer<Map<String, String>> submit, Runnable saved) {
        show(owner,title,fields,Map.of(),(values,selections)->submit.accept(values),saved);
    }
    public static void show(Component owner,String title,List<FormField> fields,
            Map<String,MultiSelectField> selections,
            java.util.function.BiConsumer<Map<String,String>,Map<String,List<String>>> submit,Runnable saved) {
        showInteractive(owner,title,fields,selections,(values,selected,controller)->controller.saveAsync(()->{
            submit.accept(values,selected);return true;
        }),saved);
    }
    public static void showInteractive(Component owner,String title,List<FormField> fields,
            Map<String,MultiSelectField> selections,InteractiveSubmit submit,Runnable saved) {
        Map<String,JComponent> components=new LinkedHashMap<>();
        components.putAll(selections);
        showWithComponents(owner,title,fields,components,submit,saved);
    }
    public static void showWithComponents(Component owner,String title,List<FormField> fields,
            Map<String,? extends JComponent> selections,InteractiveSubmit submit,Runnable saved) {
        JDialog dialog=PopupDialog.create(owner, title);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        Card form=new Card();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(javax.swing.BorderFactory.createEmptyBorder(
            Theme.SPACE_4, Theme.SPACE_6, Theme.SPACE_4, Theme.SPACE_6));
        Map<String, JComponent> inputs=new LinkedHashMap<>();
        Map<String, JLabel> fieldErrors=new LinkedHashMap<>();
        for(FormField f:fields) {
            JLabel label=new JLabel(f.label()+(f.required()?" *":""));
            if(f.required())label.setText("<html>"+f.label()+" <font color='#"
                +String.format("%06x",Theme.DANGER.getRGB()&0xffffff)+"'>*</font></html>");
            label.setFont(Theme.font(Theme.TYPE_BODY, java.awt.Font.BOLD));
            JComponent input=selections.containsKey(f.label())?selections.get(f.label()):f.allergyInput()?new AllergyField(f.initial()):f.rows()>0?new LimitedTextArea(f):f.choices()!=null?new JComboBox<>(f.choices()):f.secret()?new JPasswordField(24):new JTextField(f.initial(), 24);
            if(input instanceof JComboBox<?> c)c.setSelectedItem(f.initial());
            input.getAccessibleContext().setAccessibleName(f.label());
            label.setLabelFor(input);
            if(!(input instanceof MultiSelectField) && !(input instanceof LimitedTextArea)
                    && !(input instanceof AllergyField) && !(input instanceof FormValueComponent)) {
                Theme.field(input);
                input.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.FIELD_HEIGHT));
            }
            JPanel group=new JPanel();
            group.setOpaque(false);
            group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            input.setAlignmentX(Component.LEFT_ALIGNMENT);
            group.add(label);
            group.add(Box.createVerticalStrut(Theme.SPACE_1));
            group.add(input);
            JLabel fieldError=new JLabel(" ");
            fieldError.setFont(Theme.font(Theme.TYPE_CAPTION, java.awt.Font.PLAIN));
            fieldError.setForeground(Theme.DANGER);
            fieldError.setAlignmentX(Component.LEFT_ALIGNMENT);
            fieldErrors.put(f.label(), fieldError);
            group.add(fieldError);
            group.setAlignmentX(Component.LEFT_ALIGNMENT);
            int componentHeight=input instanceof MultiSelectField?290:input instanceof AllergyField?220:
                input instanceof LimitedTextArea?150:input instanceof FormValueComponent?input.getPreferredSize().height+48:90;
            group.setMaximumSize(new Dimension(Integer.MAX_VALUE,componentHeight));
            form.add(group);
            form.add(Box.createVerticalStrut(Theme.SPACE_2));
            inputs.put(f.label(), input);
        }
        JLabel error=new JLabel(" ");
        error.setForeground(Theme.DANGER);
        JButton save=Theme.button("Save", true), cancel=Theme.button("Cancel", false);
        JPanel bottom=new JPanel(new BorderLayout());
        JPanel buttons=new JPanel();
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(save);
        error.setFont(Theme.font(Theme.TYPE_CAPTION, java.awt.Font.PLAIN));
        bottom.add(error, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);
        save.addActionListener(e-> {
            Map<String, String> values=new LinkedHashMap<>();
            Map<String,List<String>> selectedValues=new LinkedHashMap<>();
            for(FormField f:fields) {
                JComponent c=inputs.get(f.label());
                if(c instanceof MultiSelectField multi) {
                    List<String> selected=multi.getSelectedValues();
                    if(f.required()&&selected.isEmpty()) {
                        fieldErrors.get(f.label()).setText("Select at least one symptom");
                        JLabel validation=fieldErrors.get(f.label());
                        validation.scrollRectToVisible(new Rectangle(0,0,validation.getWidth(),validation.getHeight()));
                        return;
                    }
                    fieldErrors.get(f.label()).setText(" ");
                    selectedValues.put(f.label(),selected);
                    continue;
                }
                String v;
                if(c instanceof JPasswordField pw) {
                    char[] chars=pw.getPassword();
                    v=new String(chars);
                    Arrays.fill(chars, '\0');
                }
                else v=c instanceof FormValueComponent custom?custom.formValue()
                    :c instanceof AllergyField allergy?allergy.getText()
                    :c instanceof LimitedTextArea area?area.getText()
                    :c instanceof JTextField t?t.getText().trim()
                    :c instanceof JComboBox<?> combo?Objects.toString(combo.getSelectedItem(),"")
                    :throwUnsupportedComponent(c);
                String validation = f.validator().apply(v);
                if (validation != null) {
                    fieldErrors.get(f.label()).setText(validation);
                    error.setText(" ");
                    fieldErrors.get(f.label()).scrollRectToVisible(new Rectangle(0,0,1,20));
                    c.requestFocusInWindow();
                    return;
                }
                if(f.required()&&v.isBlank()) {
                    fieldErrors.get(f.label()).setText(f.label()+" is required.");
                    error.setText(" ");
                    c.requestFocusInWindow();
                    return;
                }
                fieldErrors.get(f.label()).setText(" ");
                values.put(f.label(), v);
            }
            SaveController controller=new SaveController(save,cancel,dialog,error,saved,values::clear,()->
                inputs.values().forEach(c->{if(c instanceof JPasswordField p)p.setText("");}));
            submit.submit(Collections.unmodifiableMap(values),Collections.unmodifiableMap(selectedValues),controller);
        });
        cancel.addActionListener(e->dialog.dispose());
        dialog.getRootPane().registerKeyboardAction(e -> {
            if (cancel.isEnabled()) dialog.dispose();
        }, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        JScrollPane content=new JScrollPane(form);
        content.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        content.getVerticalScrollBar().setUnitIncrement(16);
        content.setPreferredSize(new Dimension(520, Math.min(520,
            Math.max(240, fields.size() * 86+selections.size()*200
                +(int)fields.stream().filter(f -> f.rows()>0).count()*60))));
        dialog.add(content, BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(save);
        dialog.pack();
        Rectangle screen=dialog.getGraphicsConfiguration().getBounds();
        Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(dialog.getGraphicsConfiguration());
        dialog.setSize(Math.min(dialog.getWidth(),screen.width-insets.left-insets.right),
            Math.min(dialog.getHeight(),screen.height-insets.top-insets.bottom));
        dialog.setMinimumSize(new Dimension(Math.min(360,dialog.getWidth()),Math.min(300,dialog.getHeight())));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
    private static String throwUnsupportedComponent(JComponent component) {
        throw new IllegalStateException("Unsupported custom form component: "+component.getClass().getName());
    }
    @FunctionalInterface public interface InteractiveSubmit {
        void submit(Map<String,String> values,Map<String,List<String>> selections,SaveController controller);
    }
    public static final class SaveController {
        private final JButton save,cancel;
        private final JDialog dialog;
        private final JLabel error;
        private final Runnable saved;
        private final Runnable cleanup;
        private final Runnable successCleanup;
        private SaveController(JButton save,JButton cancel,JDialog dialog,JLabel error,Runnable saved,Runnable cleanup,Runnable successCleanup) {
            this.save=save;this.cancel=cancel;this.dialog=dialog;this.error=error;this.saved=saved;this.cleanup=cleanup;this.successCleanup=successCleanup;
        }
        public void saveAsync(java.util.concurrent.Callable<Boolean> work) {
            save.setEnabled(false);cancel.setEnabled(false);
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            error.setForeground(Theme.MUTED);error.setText("Saving...");
            AsyncLoader.load(work,ok->{
                if(ok) {
                    cleanup.run();
                    successCleanup.run();
                    dialog.dispose();
                    saved.run();
                } else reset("");
            },failure->{cleanup.run();reset(MessagePopup.errorText(failure));});
        }
        private void reset(String message) {
                save.setEnabled(true);
                cancel.setEnabled(true);
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                error.setForeground(message.isBlank()?Theme.MUTED:Theme.DANGER);
                error.setText(message.isBlank()?" ":message);
        }
    }
}
