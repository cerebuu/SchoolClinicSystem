import clinic.app.ApplicationContext;
import clinic.app.Theme;
import clinic.model.User;
import clinic.service.AuditLog;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Native controls audit over isolated demo data; outbound AI and guardian sends are not invoked. */
public final class ScreenControlAudit {
    record Screen(String name,Container root) {}
    private static final AtomicReference<String> uncaught=new AtomicReference<>();
    private static int attempted,manual;

    public static void main(String[] args)throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->uncaught.compareAndSet(null,
            thread.getName()+" "+error.getClass().getSimpleName()+": "+error.getMessage()));
        boolean headless=GraphicsEnvironment.isHeadless();
        Theme.install();AuditLog audit=new AuditLog();
        try(ApplicationContext app=new ApplicationContext(true,audit)) {
            ScreenAuditFixtures.seed(app);JFrame frame=headless?null:new JFrame("SCMS control audit");
            if(frame!=null){frame.setSize(1000,700);frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);}
            for(String id:List.of("N001","A001","D001","AD001")) {
                int roleStart=attempted,manualStart=manual;
                app.login.login(id,ScreenAuditFixtures.password(id)).orElseThrow();User user=app.current();
                List<Screen> screens=ScreenAuditFixtures.screens(app,user);
                for(Screen screen:screens) {
                    if(frame!=null)SwingUtilities.invokeAndWait(()->{frame.setContentPane((Container)screen.root());frame.setVisible(true);frame.revalidate();frame.repaint();});
                    if(frame!=null)Thread.sleep(300);
                    prepareTables(screen);
                    auditScreen(user,screen,headless);
                    if(headless)pages(screen).forEach(javax.swing.JComponent::removeNotify);
                }
                System.out.println(user.getRole()+" | summary | attempted="+(attempted-roleStart)+" manual="+(manual-manualStart)
                    +(uncaught.get()==null?" | uncaught=none":" | uncaught="+uncaught.get()));
                app.login.logout();
            }
            if(frame!=null)SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    private static void auditScreen(User user,Screen screen,boolean headless)throws Exception {
        List<Component> controls=descendants(screen.root()).stream().filter(ScreenControlAudit::interactive).toList();
        if(controls.isEmpty()) {
            manual++;attempted++;
            System.out.println(user.getRole()+" | "+screen.name()+" | no clickable controls | NEEDS DECISION (empty state)");
        }
        for(Component control:controls) {
            String label=controlName(control);String result="PASS";
            if(unsafeOutbound(label)) {result="NEEDS MANUAL CHECK (external send not invoked)";manual++;}
            else if(control instanceof JTable table&&table.getRowCount()==0)result="PASS (empty table; no row to select)";
            else if(!control.isEnabled())result="PASS (disabled for current state)";
            else if(!headless&&!control.isShowing()) {result="NEEDS MANUAL CHECK (not visible)";manual++;}
            else {
                try {trigger(control);}
                catch(Throwable failure){Throwable detail=failure instanceof java.lang.reflect.InvocationTargetException
                    &&failure.getCause()!=null?failure.getCause():failure;
                    result=headless&&causedByHeadless(detail)
                    ?"NEEDS MANUAL CHECK (desktop dialog)":"ERROR "+detail.getClass().getSimpleName()+": "+detail.getMessage();
                    if(result.startsWith("NEEDS"))manual++;}
                if(uncaught.get()!=null)result="ERROR "+uncaught.get();
            }
            attempted++;System.out.println(user.getRole()+" | "+screen.name()+" | "+label+" | "+result);
            if(control instanceof JTable table&&table.getRowCount()>0&&hasActionsColumn(table)) {
                manual++;attempted++;
                System.out.println(user.getRole()+" | "+screen.name()+" | table row-action menu / Shift+F10 | NEEDS MANUAL CHECK (desktop menu)");
            }
        }
    }

    private static List<clinic.app.ui.common.PagedTablePanel<?>> pages(Screen screen) {
        List<clinic.app.ui.common.PagedTablePanel<?>> result=new ArrayList<>();
        for(Component component:descendants(screen.root()))
            if(component instanceof clinic.app.ui.common.PagedTablePanel<?> page)result.add(page);
        return result;
    }

    private static boolean hasActionsColumn(JTable table) {
        for(int column=0;column<table.getColumnCount();column++)
            if("Actions".equals(table.getColumnName(column)))return true;
        return false;
    }

    private static void prepareTables(Screen screen)throws Exception {
        List<clinic.app.ui.common.PagedTablePanel<?>> pages=pages(screen);
        if(pages.isEmpty())return;
        SwingUtilities.invokeAndWait(()->pages.forEach(page->{
            if(!page.isDisplayable())page.addNotify();
            page.reload();
        }));
        long deadline=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<deadline) {
            AtomicReference<Boolean> ready=new AtomicReference<>(false);
            SwingUtilities.invokeAndWait(()->ready.set(pages.stream().allMatch(page->
                descendants(page).stream().filter(JTable.class::isInstance).map(JTable.class::cast)
                    .allMatch(JTable::isEnabled))));
            if(ready.get())return;
            Thread.sleep(20);
        }
        System.out.println("AUDIT | "+screen.name()+" | table loading | NEEDS MANUAL CHECK (timeout)");
    }

    private static boolean causedByHeadless(Throwable failure) {
        for(Throwable current=failure;current!=null;current=current.getCause())
            if(current instanceof java.awt.HeadlessException)return true;
        return false;
    }

    private static void trigger(Component control)throws Exception {
        SwingUtilities.invokeAndWait(()->{
            Timer cleanup=new Timer(100,event->{closeDialogs();((Timer)event.getSource()).stop();});cleanup.setRepeats(false);cleanup.start();
            if(control instanceof AbstractButton button)button.doClick(0);
            else if(control instanceof JComboBox<?> combo) {if(combo.getItemCount()>0)combo.setSelectedIndex(0);else throw new IllegalStateException("empty choice list");}
            else if(control instanceof JTextField field)field.postActionEvent();
            else if(control instanceof JTextArea area) {
                Object key=area.getInputMap().get(javax.swing.KeyStroke.getKeyStroke("ENTER"));
                var action=key==null?null:area.getActionMap().get(key);if(action!=null)action.actionPerformed(null);
            } else if(control instanceof JSpinner spinner)spinner.setValue(spinner.getValue());
            else if(control instanceof JTable table&&table.getRowCount()>0) {
                table.changeSelection(0,0,false,false);
            } else throw new IllegalStateException("no action available");
        });
        Thread.sleep(140);SwingUtilities.invokeAndWait(()->{});
    }

    private static void closeDialogs() {
        for(Window window:Window.getWindows())if(window instanceof JDialog dialog&&dialog.isShowing()) {
            JButton button=descendants(dialog).stream().filter(c->c instanceof JButton b
                &&List.of("Cancel","Close","OK").contains(b.getText())&&b.isEnabled())
                .map(JButton.class::cast).findFirst().orElse(null);
            if(button!=null)button.doClick(0);else dialog.dispose();
        }
    }
    private static boolean unsafeOutbound(String label) {
        String value=label.toLowerCase(java.util.Locale.ROOT);
        return value.contains("send")||value.contains("emergency notify")||value.contains("ai send");
    }
    private static boolean interactive(Component c) {
        if(c instanceof JTextField field&&field.getParent() instanceof JComboBox<?>)return false;
        return c instanceof AbstractButton||c instanceof JComboBox<?>||c instanceof JTextField
            ||c instanceof JTextArea||c instanceof JSpinner||c instanceof JTable||c instanceof JMenuItem;
    }
    private static String controlName(Component c) {
        if(c instanceof JButton&&javax.swing.SwingUtilities.getAncestorOfClass(
                javax.swing.JScrollBar.class,c)!=null)return "scrollbar arrow";
        if(c instanceof AbstractButton b)return "button["+first(b.getText(),b.getAccessibleContext().getAccessibleName(),b.getToolTipText(),b.getName(),b.getClass().getSimpleName())+"]";
        if(c instanceof JComboBox<?> b)return "combo["+b.getAccessibleContext().getAccessibleName()+"]";
        if(c instanceof JTextField f)return "field["+f.getAccessibleContext().getAccessibleName()+"]";
        if(c instanceof JTextArea a)return "text-area["+a.getAccessibleContext().getAccessibleName()+"]";
        if(c instanceof JSpinner s)return "spinner["+s.getAccessibleContext().getAccessibleName()+"]";
        if(c instanceof JTable t)return "table["+t.getAccessibleContext().getAccessibleName()+"]";
        return c.getClass().getSimpleName();
    }
    private static String first(String... values) {
        for(String value:values)if(value!=null&&!value.isBlank())return value;
        return "unnamed";
    }
    private static List<Component> descendants(Container parent) {
        List<Component> result=new ArrayList<>();for(Component child:parent.getComponents()) {
            result.add(child);if(child instanceof Container container)result.addAll(descendants(container));
        }return result;
    }
}
