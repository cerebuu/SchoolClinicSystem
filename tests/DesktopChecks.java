import clinic.app.*;
import clinic.model.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.WindowEvent;
import java.awt.datatransfer.DataFlavor;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import javax.swing.*;

/** Native-window integration checks. Uses isolated in-memory test data only. */
public final class DesktopChecks {
    private static int checks;
    private static JFrame frame;
    private static ApplicationContext app;
    private static void edt(Runnable work) throws Exception { SwingUtilities.invokeAndWait(work); }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
        System.out.println("PASS " + name);
    }
    private static void sessionBoundResultIsDiscardedAfterSignOut() throws Exception {
        Container previous=frame.getContentPane();
        JPanel owner=new JPanel();
        edt(()->frame.setContentPane(owner));
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);
        AtomicBoolean delivered=new AtomicBoolean();
        clinic.app.ui.common.Actions.runInSession(owner,app.login,()->{
            started.countDown();
            try { release.await(5,TimeUnit.SECONDS); }
            finally { finished.countDown(); }
            return "Synthetic patient details";
        },result->delivered.set(true));
        if(!started.await(5,TimeUnit.SECONDS))throw new AssertionError("Background action did not start.");
        app.login.logout();
        app.login.login("root","test-password").orElseThrow();
        release.countDown();
        if(!finished.await(5,TimeUnit.SECONDS))throw new AssertionError("Background action did not finish.");
        for(int attempt=0;attempt<5;attempt++) {
            edt(()->{});
            Thread.sleep(20);
        }
        check(!delivered.get(),"sensitive async result is discarded after sign out and reauthentication");
        edt(()->frame.setContentPane(previous));
    }
    private static void waitFor(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < end) {
            AtomicBoolean done = new AtomicBoolean();
            edt(() -> done.set(condition.getAsBoolean()));
            if (done.get()) return;
            Thread.sleep(30);
        }
        throw new AssertionError("Timed out waiting for UI");
    }
    private static List<Component> components(Container parent) {
        List<Component> all = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            all.add(child);
            if (child instanceof Container container) all.addAll(components(container));
        }
        return all;
    }
    private static JButton button(Container parent, String text) {
        return components(parent).stream().filter(c -> c instanceof JButton b
            && text.equals(b.getText()) && b.isShowing()).map(c -> (JButton)c).findFirst().orElseThrow();
    }
    private static void click(Container parent, String text) {
        SwingUtilities.invokeLater(() -> button(parent, text).doClick());
    }
    private static boolean visibleButton(Container parent,String text) {
        return components(parent).stream().anyMatch(c->c instanceof JButton b&&text.equals(b.getText())&&b.isShowing());
    }
    private static void clickCopyForId(Container parent,String label) throws Exception {
        edt(()->{
            JButton button=components(parent).stream().filter(c->c instanceof JButton copy
                &&("copy:"+label).equals(copy.getName())).map(c->(JButton)c).findFirst().orElseThrow();
            button.doClick();
        });
    }
    private static int column(JTable table,String name) {
        for(int c=0;c<table.getColumnCount();c++)if(name.equals(table.getColumnName(c)))return c;
        throw new AssertionError("Missing column: "+name);
    }
    private static void clickCell(JTable table,int row,int column,boolean right,boolean copyIcon) throws Exception {
        edt(()->{
            Rectangle cell=table.getCellRect(row,column,true);
            int x=copyIcon?cell.x+cell.width-12:cell.x+Math.min(32,cell.width/2);
            int y=cell.y+cell.height/2,button=right?java.awt.event.MouseEvent.BUTTON3:java.awt.event.MouseEvent.BUTTON1;
            int mask=right?InputEvent.BUTTON3_DOWN_MASK:InputEvent.BUTTON1_DOWN_MASK;long now=System.currentTimeMillis();
            table.dispatchEvent(new java.awt.event.MouseEvent(table,java.awt.event.MouseEvent.MOUSE_PRESSED,
                now,mask,x,y,1,false,button));
            table.dispatchEvent(new java.awt.event.MouseEvent(table,java.awt.event.MouseEvent.MOUSE_RELEASED,
                now+1,0,x,y,1,right,button));
            table.dispatchEvent(new java.awt.event.MouseEvent(table,java.awt.event.MouseEvent.MOUSE_CLICKED,
                now+2,0,x,y,1,false,button));
        });
    }
    private static String clipboardText() throws Exception {
        return (String)Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
    }
    private static void setClipboardText(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(text),null);
    }
    private static boolean copiedFeedback(JTable table,int row,int column) {
        Component cell=table.prepareRenderer(table.getCellRenderer(row,column),row,column);
        return components((Container)cell).stream().anyMatch(c->c instanceof JLabel label&&"Copied".equals(label.getText()));
    }
    private static void consultationCopyDemo() throws Exception {
        check(visibleButton(frame,"+ New Consultation"),"Consultations retains + New Consultation");
        edt(()->frame.setSize(1000,700));
        JTable visits=table();int idColumn=column(visits,"Record ID"),row=0;
        String id=String.valueOf(visits.getValueAt(row,idColumn));
        var idView=visits.getColumnModel().getColumn(idColumn);
        check(idView.getMinWidth()>=154&&idView.getResizable(),"Record ID column is resizable and wide enough at 1000x700");
        AtomicReference<String> shown=new AtomicReference<>();
        edt(()->shown.set(components((Container)visits.prepareRenderer(visits.getCellRenderer(row,idColumn),row,idColumn))
            .stream().filter(c->c instanceof JLabel).map(c->((JLabel)c).getText()).findFirst().orElse("")));
        check(shown.get().equals(id.substring(0,Math.min(8,id.length()))+(id.length()>8?"…":"")),
            "Record ID displays its first eight characters and ellipsis");
        Rectangle cell=visits.getCellRect(row,idColumn,true);
        String tooltip=visits.getToolTipText(new java.awt.event.MouseEvent(visits,java.awt.event.MouseEvent.MOUSE_MOVED,
            System.currentTimeMillis(),0,cell.x+12,cell.y+cell.height/2,0,false));
        check(id.equals(tooltip),"hover tooltip exposes the full ID");
        clickCell(visits,row,idColumn,false,true);
        String copied=clipboardText();System.out.println("CLIPBOARD_READBACK="+copied);
        check(id.equals(copied),"one click on the consultation copy icon copies the full ID");
        AtomicBoolean feedback=new AtomicBoolean();edt(()->feedback.set(copiedFeedback(visits,row,idColumn)));
        check(feedback.get(),"Copied feedback is visible after copying");
        Thread.sleep(1650);
        edt(()->feedback.set(copiedFeedback(visits,row,idColumn)));
        check(!feedback.get(),"Copied feedback disappears after about 1.5 seconds");
        clickCell(visits,row,idColumn,false,false);
        check(id.equals(clipboardText()),"one click on the ID text copies the full ID");
        edt(visits::clearSelection);
        clickCell(visits,row,column(visits,"Concern"),false,false);
        check(visits.getSelectedRow()==row,"clicking a non-ID cell still selects its row");
        clickCell(visits,row,idColumn,true,false);
        waitFor(()->findMenuItem("Copy ID")!=null);
        SwingUtilities.invokeLater(()->findMenuItem("Copy ID").doClick());
        waitFor(()->id.equals(clipboardTextUnchecked()));
        check(id.equals(clipboardText()),"right-click Copy ID copies the full ID");
        rowAction("View details");JDialog detail=awaitCopyDialog("School clinic","Record ID");
        setClipboardText("clipboard-marker");
        clickCopyForId(detail,"Record ID");
        waitFor(()->id.equals(clipboardTextUnchecked()));
        check(id.equals(clipboardText()),"consultation detail Copy button copies only the Record ID");
        click(detail,"OK");waitFor(()->!detail.isShowing());
        var page=components(frame).stream().filter(c->c instanceof clinic.app.ui.common.PagedTablePanel<?> p
            &&"ConsultationsTableModel".equals(p.modelType())&&p.isShowing())
            .map(c->(clinic.app.ui.common.PagedTablePanel<?>)c).findFirst().orElseThrow();
        int refresh=page.refreshCount();app.changes.publish(DataTopic.VISITS);
        waitFor(()->page.refreshCount()>refresh&&visits.isEnabled()&&visits.getRowCount()>0);
        clickCell(visits,row,idColumn,false,true);
        check(id.equals(clipboardText()),"copy icon works after an automatic table refresh");
    }
    private static String clipboardTextUnchecked() {
        try{return clipboardText();}catch(Exception error){return "";}
    }
    private static void printInventoryHistoryEvents() {
        String medicine="History event demo";
        app.inventoryBatchSession(medicine,false,false).accept(Map.of("Quantity","12",
            "Expiry (YYYY-MM-DD)",LocalDate.now().plusDays(200).toString(),"Remarks",""));
        app.inventoryBatchSession(medicine,true,false).accept(Map.of("Quantity","12",
            "Expiry (YYYY-MM-DD)",LocalDate.now().plusDays(190).toString(),"Remarks",""));
        var events=app.medicationHistory(0,100,"",null,null).rows().stream()
            .filter(entry->medicine.equals(entry.medicine().name())).toList();
        long initial=events.stream().filter(entry->entry.type()==MedicationHistoryEntry.Type.INITIAL_STOCK).count();
        long restock=events.stream().filter(entry->entry.type()==MedicationHistoryEntry.Type.RESTOCK).count();
        check(events.size()==2&&initial==1&&restock==1,"one Add medicine writes one INITIAL_STOCK and one restock writes one RESTOCK");
        System.out.println("HISTORY_EVENTS add medicine=[INITIAL_STOCK] restock=[RESTOCK] counts="+initial+","+restock);
    }
    private static JDialog dialog(String title) {
        return Arrays.stream(Window.getWindows()).filter(w -> w instanceof JDialog d
            && d.isShowing() && title.equals(d.getTitle())).map(w -> (JDialog)w)
            .findFirst().orElse(null);
    }
    private static JDialog awaitDialog(String title) throws Exception {
        try {
            waitFor(() -> dialog(title) != null);
        } catch (AssertionError timeout) {
            AtomicReference<String> visibleDialogs = new AtomicReference<>("none");
            edt(() -> visibleDialogs.set(Arrays.stream(Window.getWindows())
                .filter(w -> w instanceof JDialog d && d.isShowing())
                .map(w -> ((JDialog)w).getTitle())
                .reduce((left, right) -> left + "; " + right).orElse("none")));
            throw new AssertionError("Timed out waiting for dialog '" + title
                + "'. Visible dialogs: " + visibleDialogs.get(), timeout);
        }
        AtomicReference<JDialog> result = new AtomicReference<>();
        edt(() -> result.set(dialog(title)));
        return result.get();
    }
    private static JDialog awaitCopyDialog(String title,String label) throws Exception {
        waitFor(()->Arrays.stream(Window.getWindows()).filter(w->w instanceof JDialog d&&d.isShowing()
                &&title.equals(d.getTitle()))
            .anyMatch(w->components((Container)w).stream().anyMatch(c->c instanceof JButton b
                &&("copy:"+label).equals(b.getName()))));
        return Arrays.stream(Window.getWindows()).filter(w->w instanceof JDialog d&&d.isShowing()
                &&title.equals(d.getTitle()))
            .filter(w->components((Container)w).stream().anyMatch(c->c instanceof JButton b
                &&("copy:"+label).equals(b.getName())))
            .map(w->(JDialog)w).findFirst().orElseThrow();
    }
    private static JDialog awaitDialogContaining(String title,String text) throws Exception {
        waitFor(()->Arrays.stream(Window.getWindows()).filter(w->w instanceof JDialog d&&d.isShowing()
                &&title.equals(d.getTitle()))
            .anyMatch(w->components((Container)w).stream().anyMatch(c->c instanceof JTextArea area
                &&area.getText().contains(text))));
        return Arrays.stream(Window.getWindows()).filter(w->w instanceof JDialog d&&d.isShowing()
                &&title.equals(d.getTitle()))
            .filter(w->components((Container)w).stream().anyMatch(c->c instanceof JTextArea area
                &&area.getText().contains(text)))
            .map(w->(JDialog)w).findFirst().orElseThrow();
    }
    private static void fill(Container parent, String name, String value) {
        var multi=components(parent).stream().filter(c->c instanceof clinic.app.ui.common.popup.MultiSelectField m
            &&name.equals(m.getAccessibleContext().getAccessibleName())).findFirst();
        if(multi.isPresent()) {
            components((Container)multi.get()).stream().filter(c->c instanceof JCheckBox box&&value.equals(box.getText()))
                .map(c->(JCheckBox)c).findFirst().orElseThrow().doClick();
            return;
        }
        Component input = components(parent).stream().filter(c -> (c instanceof JTextField || c instanceof JComboBox<?>) && c instanceof JComponent j
            && (name.equals(j.getName()) || name.equals(j.getAccessibleContext().getAccessibleName())))
            .findFirst().orElseThrow(() -> new AssertionError("Missing input: " + name));
        if (input instanceof JTextField field) field.setText(value);
        else if (input instanceof JComboBox<?> combo) combo.setSelectedItem(value);
    }
    private static void form(String title, Map<String, String> values) throws Exception {
        JDialog dialog = awaitDialog(title);
        if(values.containsKey("Medicine"))selectMedicine(dialog,values.get("Medicine"));
        edt(() -> {
            values.forEach((name,value)->{if(!name.equals("Medicine"))fill(dialog,name,value);});
            triggerPatientLookups(dialog);
        });
        waitFor(() -> button(dialog,"Save").isEnabled());
        click(dialog, "Save");
        waitFor(() -> !dialog.isShowing());
    }
    private static JDialog createNotificationForm() throws Exception {
        click(frame, "Create notification");
        return awaitDialog("Create notification");
    }
    private static void readyNotification(JDialog dialog) throws Exception {
        edt(() -> {
            fill(dialog, "Patient ID", "p1");
            triggerPatientLookups(dialog);
        });
        waitFor(() -> button(dialog, "Save").isEnabled());
        notificationState("ready", dialog);
    }
    private static void notificationState(String step,JDialog dialog) {
        String transaction=components(dialog).stream().filter(c->c instanceof JTextField field
            &&"Transaction ID".equals(field.getName())).map(c->((JTextField)c).getText()).findFirst().orElse("");
        String type=components(dialog).stream().filter(c->c instanceof JComboBox<?> combo
            &&"Notification type".equals(combo.getName())).map(c->String.valueOf(((JComboBox<?>)c).getSelectedItem()))
            .findFirst().orElse("");
        System.out.println("FIELD notification "+step+" | Patient ID="+textField(dialog,"Patient ID").getText()
            +" | Transaction ID="+transaction+" | Type="+type
            +" | Save enabled="+buttonEnabled(dialog,"Save")
            +" | Cancel enabled="+buttonEnabled(dialog,"Cancel")
            +" | Popup visible="+dialog.isShowing());
    }
    private static boolean buttonEnabled(Container parent,String name) {
        return components(parent).stream().filter(c->c instanceof JButton button&&name.equals(button.getText()))
            .map(c->((JButton)c).isEnabled()).findFirst().orElse(false);
    }
    private static void pressEscape(JDialog dialog) throws Exception {
        edt(() -> {
            JRootPane root = dialog.getRootPane();
            Object binding = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .get(KeyStroke.getKeyStroke("ESCAPE"));
            Action action = binding == null ? null : root.getActionMap().get(binding);
            if (action == null) throw new AssertionError("Escape action is unavailable.");
            action.actionPerformed(new java.awt.event.ActionEvent(root,
                java.awt.event.ActionEvent.ACTION_PERFORMED, "ESCAPE"));
        });
    }
    private static void triggerPatientLookups(Container parent) {
        components(parent).stream().filter(c->c instanceof clinic.app.ui.common.PatientLookupField)
            .map(c->(clinic.app.ui.common.PatientLookupField)c)
            .forEach(field->field.textField().postActionEvent());
    }
    private static void selectMedicine(Container parent, String name) throws Exception {
        waitFor(() -> components(parent).stream().filter(c->c instanceof JComboBox<?> combo
            &&"Medicine".equals(combo.getAccessibleContext().getAccessibleName())).map(c->(JComboBox<?>)c)
            .anyMatch(combo->{for(int i=0;i<combo.getItemCount();i++)if(combo.getItemAt(i) instanceof Medicine m&&m.getName().equals(name))return true;return false;}));
        edt(() -> {
            JComboBox<?> combo=components(parent).stream().filter(c->c instanceof JComboBox<?> x
                &&"Medicine".equals(x.getAccessibleContext().getAccessibleName())).map(c->(JComboBox<?>)c).findFirst().orElseThrow();
            for(int i=0;i<combo.getItemCount();i++)if(combo.getItemAt(i) instanceof Medicine m&&m.getName().equals(name)) {
                combo.setSelectedItem(m);return;
            }
            throw new AssertionError("Medicine not loaded: "+name);
        });
    }
    private static void setQuantity(Container parent, int value) throws Exception {
        edt(() -> components(parent).stream().filter(c->c instanceof JSpinner s
            &&"Quantity".equals(s.getAccessibleContext().getAccessibleName())).map(c->(JSpinner)c).findFirst().orElseThrow().setValue(value));
    }
    private static JDialog newConsultationWithMedicine(String patientId, String concern,
            String medicine, int quantity) throws Exception {
        click(frame, "Consultations");
        click(frame, "+ New Consultation");
        JDialog form = awaitDialog("New Consultation");
        edt(() -> {
            fill(form, "Patient ID", patientId);
            fill(form, "Symptoms", "Headache");
            fill(form, "Concern", concern);
            triggerPatientLookups(form);
        });
        waitFor(() -> components(form).stream().filter(c->c instanceof clinic.app.ui.common.PatientLookupField)
            .map(c->(clinic.app.ui.common.PatientLookupField)c).allMatch(clinic.app.ui.common.PatientLookupField::isFormReady));
        click(form, "+ Add medicine");
        selectMedicine(form, medicine);
        setQuantity(form, quantity);
        return form;
    }
    private static void dismissInfo() throws Exception {
        JDialog info = awaitDialog("School clinic");
        click(info, "OK");
        waitFor(() -> !info.isShowing());
    }
    private static JTable table() {
        return table(frame);
    }
    private static JTable table(Container parent) {
        return components(parent).stream().filter(c -> c instanceof JTable t && t.isShowing()
            && t.isEnabled()).map(c -> (JTable)c).findFirst().orElse(null);
    }
    private static boolean showingTableModel(String type) {
        return components(frame).stream().anyMatch(c->c instanceof clinic.app.ui.common.PagedTablePanel<?> page
            &&type.equals(page.modelType())&&page.isShowing());
    }
    private static JTextField textField(Container parent,String accessibleName) {
        return components(parent).stream().filter(c -> c instanceof JTextField field
            && accessibleName.equals(field.getAccessibleContext().getAccessibleName()))
            .map(c -> (JTextField)c).findFirst().orElseThrow();
    }
    private static boolean hasLabel(String text) {
        return components(frame).stream().anyMatch(c -> c instanceof JLabel label
            && text.equals(label.getText()));
    }
    private static JLabel uicLogo() {
        return components(frame).stream().filter(c -> c instanceof JLabel label
            && "University of the Immaculate Conception seal".equals(
                label.getAccessibleContext().getAccessibleName()))
            .map(c -> (JLabel)c).findFirst().orElse(null);
    }
    private static int nurseMetricColumns() {
        return components(frame).stream()
            .filter(c -> c instanceof clinic.app.ui.dashboard.ResponsiveKpiGrid)
            .map(c -> (GridLayout)((Container)c).getLayout())
            .mapToInt(GridLayout::getColumns).findFirst().orElse(0);
    }
    private static void row() throws Exception {
        waitFor(() -> table() != null && table().getRowCount() > 0);
        edt(() -> table().setRowSelectionInterval(0, 0));
    }
    private static void rowWithValue(int column, String value) throws Exception {
        waitFor(() -> {
            JTable current = table();
            if (current == null || column >= current.getColumnCount()) return false;
            for (int r=0;r<current.getRowCount();r++)
                if (value.equals(String.valueOf(current.getValueAt(r,column)))) return true;
            return false;
        });
        edt(() -> {
            JTable current = table();
            for (int r=0;r<current.getRowCount();r++)
                if (value.equals(String.valueOf(current.getValueAt(r,column)))) {
                    current.setRowSelectionInterval(r,r);
                    return;
                }
            throw new AssertionError("Table row not found: " + value);
        });
    }
    private static void rowAction(String text) throws Exception {
        edt(() -> {
            JTable selectedTable = table();
            if (selectedTable == null || selectedTable.getSelectedRow() < 0)
                throw new AssertionError("Select a table row before opening row actions: " + text);
            Object binding = selectedTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .get(KeyStroke.getKeyStroke("shift F10"));
            Action open = binding == null ? null : selectedTable.getActionMap().get(binding);
            if (open == null) throw new AssertionError("Shift+F10 row action is unavailable.");
            open.actionPerformed(new java.awt.event.ActionEvent(selectedTable,
                java.awt.event.ActionEvent.ACTION_PERFORMED, "shift F10"));
        });
        waitFor(() -> findMenuItem(text) != null);
        SwingUtilities.invokeLater(() -> findMenuItem(text).doClick());
    }
    private static JMenuItem findMenuItem(String text) {
        for (MenuElement element : MenuSelectionManager.defaultManager().getSelectedPath()) {
            Component menu = element.getComponent();
            if (menu instanceof JMenuItem item && text.equals(item.getText()) && item.isShowing())
                return item;
            if (menu instanceof Container container) {
                JMenuItem found = components(container).stream()
                    .filter(c -> c instanceof JMenuItem item && text.equals(item.getText()) && item.isShowing())
                    .map(c -> (JMenuItem)c).findFirst().orElse(null);
                if (found != null) return found;
            }
        }
        for (Window window : Window.getWindows()) {
            JMenuItem found = components(window).stream()
                .filter(c -> c instanceof JMenuItem item && text.equals(item.getText()) && item.isShowing())
                .map(c -> (JMenuItem)c).findFirst().orElse(null);
            if (found != null) return found;
        }
        return null;
    }
    private static void doubleSubmit(JDialog dialog,String label) throws Exception {
        edt(() -> {
            JButton save = button(dialog, "Save");
            save.doClick();
            check(!save.isEnabled(), label+" Save disables while request is pending");
            save.doClick();
        });
        waitFor(() -> !dialog.isShowing());
    }
    private static long labelCount(String text) {
        return components(frame).stream().filter(c -> c instanceof JLabel label
            && text.equals(label.getText())).count();
    }
    private static void screenshot(String name) throws Exception {
        edt(() -> {
            try {
                java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                    frame.getWidth(), frame.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                frame.paintAll(g);
                g.dispose();
                javax.imageio.ImageIO.write(image, "png", Path.of("verification", name + ".png").toFile());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Desktop required");
        try {
            app = Arrays.asList(args).contains("--persistent")
                ? new ApplicationContext(Files.createTempDirectory(Path.of("verification"),"desktop-db-").resolve("clinic"))
                : new ApplicationContext(false);
            edt(() -> {
                Theme.install();
                try {
                    var cache=Class.forName("clinic.app.ui.common.MedicineCatalogCache").getDeclaredField("catalog");
                    cache.setAccessible(true);
                    cache.set(null,new clinic.service.MedicineCatalog(List.of(
                        new clinic.service.MedicineCatalog.Entry("Test medicine","A01","A02",true,true))));
                }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
                frame = new JFrame("Clinic integration checks");
                frame.setSize(1100, 700);
                new Router(frame, app).login();
                frame.setVisible(true);
            });
            waitFor(() -> uicLogo() != null && button(frame, "Sign in").isShowing());
            JLabel logo = uicLogo();
            check(logo.getIcon() instanceof ImageIcon image
                    && image.getIconWidth() == 144 && image.getIconHeight() == 144,
                "login shows a proportional, accessible UIC logo");
            check(hasLabel("UIC School Clinic") && hasLabel("Welcome back"),
                "login presents clinic identity before the sign-in form");
            screenshot("desktop-login-1100x700");
            click(frame, "Create first administrator");
            form("First-run administrator setup", Map.of("ID", "root", "Name", "Test Admin", "Password", "test-password"));
            dismissInfo();
            check(!app.login.needsSetup(), "first-run setup via dialog");
            edt(() -> { fill(frame, "User ID", "root"); fill(frame, "Password", "wrong"); });
            click(frame, "Sign in");
            waitFor(() -> button(frame, "Sign in").isEnabled());
            check(!app.login.isLoggedIn(), "UI rejects incorrect password");
            edt(() -> fill(frame, "Password", "test-password"));
            click(frame, "Sign in");
            waitFor(() -> app.login.isLoggedIn() && components(frame).stream().anyMatch(c -> c instanceof JButton b && "Users".equals(b.getText())));
            waitFor(() -> hasLabel("System-wide totals. Manage accounts in Users and campus entries in Campuses."));
            screenshot("desktop-superadmin-1100");
            check(true, "Superadmin dashboard summary and layout");
            sessionBoundResultIsDiscardedAfterSignOut();
            click(frame, "Campuses");
            click(frame, "Add campus");
            form("Add campus", Map.of("Campus ID", "main", "Name", "Main campus"));
            rowWithValue(0,"main");
            rowAction("Edit campus");
            form("Edit campus", Map.of("Name", "Updated campus"));
            waitFor(() -> {
                JTable campuses=table();
                if(campuses==null)return false;
                for(int row=0;row<campuses.getRowCount();row++)
                    if("main".equals(campuses.getValueAt(row,0))
                            &&"Updated campus".equals(campuses.getValueAt(row,1)))return true;
                return false;
            });
            check(true, "campus create/edit refresh");
            click(frame, "Users");
            waitFor(() -> table() != null);
            click(frame, "Add user");
            JDialog addUser=awaitDialog("Add user");
            edt(() -> {
                JComboBox<?> campus=components(addUser).stream().filter(c -> c instanceof JComboBox<?> combo
                    && "Assigned School / Campus".equals(combo.getAccessibleContext().getAccessibleName()))
                    .map(c -> (JComboBox<?>)c).findFirst().orElseThrow();
                boolean offersUnassigned=false;
                for(int i=0;i<campus.getItemCount();i++)
                    if("Unassigned".equals(campus.getItemAt(i)))offersUnassigned=true;
                check(!offersUnassigned,"clinical user form requires a campus assignment");
                check(campus.getItemCount()==3&&"UIC - Bajada".equals(campus.getItemAt(0))
                    &&"UIC - Annex".equals(campus.getItemAt(1))&&"UIC - Main".equals(campus.getItemAt(2)),
                    "nurse campus dropdown contains exactly the three approved schools");
                Map.of("ID", "nurse", "Name", "Test Nurse", "Role", User.NURSE,
                    "Assigned School / Campus", "UIC - Main", "Password", "test-password")
                    .forEach((name,value)->fill(addUser,name,value));
            });
            click(addUser,"Save");
            waitFor(() -> !addUser.isShowing());
            waitFor(() -> table() != null && table().getRowCount() == 2);
            check(app.admin.userCount() == 2, "user added via dialog and table refreshed");
            edt(() -> {
                boolean campusNameVisible=false;
                for(int row=0;row<table().getRowCount();row++)
                    if("nurse".equals(table().getValueAt(row,0)))
                        campusNameVisible=String.valueOf(table().getValueAt(row,3)).equals("uic-main | UIC - Main");
                check(campusNameVisible,"users table shows assigned campus name");
            });
            check("uic-main".equals(app.admin.users(0, 10, "nurse").rows().get(0).getCampusId()),
                "user form loads and saves campus assignment");
            rowWithValue(0,"nurse");
            rowAction("Edit user / role");
            JDialog editUser=awaitDialog("Edit user nurse");
            edt(() -> {
                JComboBox<?> campus=components(editUser).stream().filter(c -> c instanceof JComboBox<?> combo
                    && "Assigned School / Campus".equals(combo.getAccessibleContext().getAccessibleName()))
                    .map(c -> (JComboBox<?>)c).findFirst().orElseThrow();
                check(campus.getItemCount()==3&&"UIC - Main".equals(campus.getSelectedItem()),
                    "nurse edit selects the current approved campus");
            });
            click(editUser,"Cancel");
            waitFor(() -> !editUser.isShowing());
            check(true,"user edit remains available from its row menu");
            click(frame,"Audit log");
            waitFor(() -> components(frame).stream().anyMatch(c -> c instanceof JTextField field
                && "User / nurse ID".equals(field.getAccessibleContext().getAccessibleName())));
            edt(() -> {
                textField(frame,"Quick search: user ID / campus / action / date / denied").setText("addUser");
                textField(frame,"User / nurse ID").setText("root");
                textField(frame,"Campus ID").setText("null");
                textField(frame,"Action").setText("addUser");
                textField(frame,"Date").setText(LocalDate.now().toString());
            });
            waitFor(() -> {
                JTable auditTable=table();
                if(auditTable==null||auditTable.getRowCount()!=1)return false;
                return String.valueOf(auditTable.getValueAt(0,0)).contains(LocalDate.now().toString())
                    &&"root".equals(auditTable.getValueAt(0,1))
                    &&"Unknown".equals(auditTable.getValueAt(0,3))
                    &&"addUser".equals(auditTable.getValueAt(0,4));
            });
            screenshot("desktop-audit-filter-1100");
            click(frame,"Clear filters");
            waitFor(() -> {
                JTable auditTable=table();
                if(auditTable==null||auditTable.getRowCount()==0)return false;
                for(int row=0;row<auditTable.getRowCount();row++)
                    if(!"addUser".equals(auditTable.getValueAt(row,4)))return false;
                return true;
            });
            check(true,"combined audit filters and clear preserve quick search");
            click(frame, "Sign out");
            waitFor(() -> !app.login.isLoggedIn());
            edt(() -> { fill(frame, "User ID", "nurse"); fill(frame, "Password", "test-password"); });
            click(frame, "Sign in");
            waitFor(() -> app.login.isLoggedIn() && components(frame).stream().anyMatch(c -> c instanceof JButton b && "Patients".equals(b.getText())));
            click(frame, "Patients");
            click(frame, "Register patient");
            JDialog registration = awaitDialog("Register patient");
            waitFor(() -> !button(registration,"Save").isEnabled());
            check(app.patientCount() == 0, "required fields prevent registration");
            click(registration,"Cancel");waitFor(()->!registration.isShowing());
            click(frame, "Register patient");
            form("Register patient", Map.of("ID", "p1", "Name", "Test Patient", "Contact", "Test contact", "Type", Patient.STUDENT,
                "Course / Department", "BSCS", "Section / Position", "A", "Year level", "1", "Guardian contact", "guardian@example.test"));
            row();
            check(app.patientCount() == 1, "patient registration and refresh");
            check("guardian@example.test".equals(app.clinic().findPatient("p1").orElseThrow().getGuardianContact()), "registration saves separate guardian contact");
            rowAction("Edit patient");
            JDialog editPatient=awaitDialog("Edit patient — ID p1");
            click(editPatient,"Cancel");
            waitFor(() -> !editPatient.isShowing());
            check(true,"patient edit remains available from its row menu");
            rowAction("Add allergy");
            JDialog allergy = awaitDialog("Add allergy");
            click(allergy, "Cancel");
            waitFor(() -> !allergy.isShowing());
            check(app.clinic().findPatient("p1").orElseThrow().getAllergies().isEmpty(), "dialog cancellation leaves data unchanged");
            click(frame, "Consultations");
            waitFor(()->visibleButton(frame,"+ New Consultation"));
            check(visibleButton(frame,"+ New Consultation"),"Consultations still shows + New Consultation");
            click(frame, "+ New Consultation");
            form("New Consultation", Map.of("Patient ID", "p1", "Symptoms", "Headache", "Concern", "Test visit"));
            dismissInfo();
            row();
            check(app.clinic().getVisitHistory("p1").size() == 1, "consultation saved via form");
            consultationCopyDemo();
            rowAction("View patient history");
            JDialog visitHistory=awaitDialogContaining("School clinic","Test visit");
            edt(() -> check(components(visitHistory).stream().anyMatch(c -> c instanceof JTextArea area
                && area.getText().contains("Test visit")),"patient history menu shows the saved consultation"));
            click(visitHistory,"OK");
            waitFor(() -> !visitHistory.isShowing());
            click(frame, "Inventory");
            click(frame, "Add medicine");
            form("Add medicine batch", Map.of("Medicine", "Test medicine", "Quantity", "10", "Expiry (YYYY-MM-DD)", LocalDate.now().plusDays(90).toString()));
            row();
            JDialog administration = newConsultationWithMedicine("p1", "Administer test medicine", "Test medicine", 2);
            doubleSubmit(administration,"consultation");
            dismissInfo();
            check(app.clinic().getStock("Test medicine") == 8, "consultation administration changes stock");
            check(app.clinic().getVisitHistory("p1").size() == 2,
                "double-clicked consultation creates exactly one visit");
            click(frame, "Medication history");
            waitFor(()->showingTableModel("TransactionsTableModel"));
            check(!visibleButton(frame,"+ New consultation")&&!visibleButton(frame,"+ New Consultation"),
                "Medication history has no New Consultation button");
            row();
            rowAction("View entry");
            String transactionId=String.valueOf(table().getValueAt(table().getSelectedRow(),column(table(),"Event ID")));
            String copyIdName="DISPENSE".equals(String.valueOf(table().getValueAt(table().getSelectedRow(),column(table(),"Event"))))
                ?"Copy Transaction ID":"Copy Event ID";
            JDialog transaction=awaitCopyDialog("School clinic",copyIdName.substring("Copy ".length()));
            setClipboardText("clipboard-marker");
            clickCopyForId(transaction,copyIdName.substring("Copy ".length()));
            waitFor(()->transactionId.equals(clipboardTextUnchecked()));
            check(transactionId.equals(clipboardText()),"View transaction detail Copy button copies only its ID");
            click(transaction,"OK");waitFor(()->!transaction.isShowing());
            click(frame, "Notifications");
            int notificationCount=app.clinic().getNotifications("p1").size();
            JDialog canceledNotification=createNotificationForm();
            readyNotification(canceledNotification);
            click(canceledNotification,"Cancel");
            waitFor(()->!canceledNotification.isShowing());
            notificationState("Cancel",canceledNotification);
            check(app.clinic().getNotifications("p1").size()==notificationCount,
                "notification Cancel closes without saving");
            JDialog escapedNotification=createNotificationForm();
            readyNotification(escapedNotification);
            pressEscape(escapedNotification);
            waitFor(()->!escapedNotification.isShowing());
            notificationState("Escape",escapedNotification);
            check(app.clinic().getNotifications("p1").size()==notificationCount,
                "notification Escape closes without saving");
            JDialog closedNotification=createNotificationForm();
            readyNotification(closedNotification);
            edt(()->closedNotification.dispatchEvent(new WindowEvent(closedNotification,WindowEvent.WINDOW_CLOSING)));
            waitFor(()->!closedNotification.isShowing());
            notificationState("window close",closedNotification);
            check(app.clinic().getNotifications("p1").size()==notificationCount,
                "notification window close closes without saving");
            Patient original=app.clinic().findPatient("p1").orElseThrow();
            Student missingGuardian=new Student("p1",original.getName(),original.getContactInfo(),"BSCS",1,"A");
            JDialog failedNotification=createNotificationForm();
            readyNotification(failedNotification);
            app.clinic().updatePatient("p1",missingGuardian);
            click(failedNotification,"Save");
            JDialog failureMessage=awaitDialog("Unable to complete");
            click(failureMessage,"OK");
            waitFor(()->!failureMessage.isShowing());
            notificationState("failed Save recovery",failedNotification);
            edt(()->check(failedNotification.isShowing()
                &&"p1".equals(textField(failedNotification,"Patient ID").getText())
                &&button(failedNotification,"Save").isEnabled(),
                "failed notification save preserves the patient and keeps the form usable"));
            click(failedNotification,"Cancel");
            waitFor(()->!failedNotification.isShowing());
            check(app.clinic().getNotifications("p1").size()==notificationCount,
                "failed notification save creates no record");
            app.clinic().updatePatient("p1",original);
            JDialog notification=createNotificationForm();
            readyNotification(notification);
            doubleSubmit(notification,"notification");
            notificationState("double Save",notification);
            waitFor(()->app.clinic().getNotifications("p1").size()==notificationCount+1);
            check(app.clinic().getNotifications("p1").size()==notificationCount+1,
                "double-click notification Save creates one record");
            row();
            check(java.util.stream.IntStream.range(0,table().getRowCount())
                .anyMatch(index->"p1".equals(String.valueOf(table().getValueAt(index,1)))),
                "notification table updates automatically without Refresh");
            click(frame, "View message");
            JTable notificationTable=table();
            String notificationId=String.valueOf(notificationTable.getValueAt(notificationTable.getSelectedRow(),column(notificationTable,"ID")));
            JDialog message=awaitCopyDialog("School clinic","Notification ID");
            setClipboardText("clipboard-marker");
            clickCopyForId(message,"Notification ID");
            waitFor(()->notificationId.equals(clipboardTextUnchecked()));
            check(notificationId.equals(clipboardText()),"View message Copy button copies only the notification ID");
            click(message,"OK");waitFor(()->!message.isShowing());
            click(frame, "Mark sent (manual)");
            click(awaitDialog("Confirm"), "OK");
            waitFor(() -> app.clinic().getNotifications("p1").get(0).getStatus() == NotificationStatus.SENT);
            check(true, "notification creation, preview, and confirmed status update");
            app.clinic().addAllergy("p1", "Test medicine");
            JDialog canceledAdministration = newConsultationWithMedicine("p1", "Cancelled allergy check", "Test medicine", 2);
            click(canceledAdministration, "Save");
            JDialog warning = awaitDialog("Confirm allergies before dispensing");
            edt(() -> check(!button(warning, "Dispense").isEnabled(), "dispensing requires checkbox confirmation"));
            click(warning, "Cancel");
            waitFor(() -> !warning.isShowing());
            click(canceledAdministration, "Cancel");
            waitFor(() -> !canceledAdministration.isShowing());
            check(app.clinic().getStock("Test medicine") == 8, "cancel safety warning preserves stock");
            JDialog confirmedAdministration = newConsultationWithMedicine("p1", "Confirmed allergy check", "Test medicine", 2);
            click(confirmedAdministration, "Save");
            final JDialog override = awaitDialog("Confirm allergies before dispensing");
            edt(() -> components(override).stream().filter(c -> c instanceof JCheckBox box
                && box.getText().equals("I have checked and it is safe to dispense"))
                .map(c -> (JCheckBox)c).findFirst().orElseThrow().doClick());
            click(override, "Dispense");
            dismissInfo();
            check(app.clinic().getStock("Test medicine") == 6, "documented override dispenses medicine");
            click(frame, "Inventory");
            waitFor(() -> table() != null && Integer.valueOf(6).equals(table().getValueAt(0, column(table(),"Eligible quantity"))));
            check(true, "cached inventory reloads after dispensing");
            row();
            rowAction("Restock");
            form("Restock Test medicine", Map.of("Quantity", "12", "Expiry (YYYY-MM-DD)", LocalDate.now().plusDays(100).toString()));
            waitFor(() -> table() != null && table().getRowCount() == 1
                && Integer.valueOf(18).equals(table().getValueAt(0, column(table(),"Eligible quantity"))));
            check(app.clinic().getStock("Test medicine") == 18, "restock creates batch and updates total");
            screenshot("desktop-inventory-1100");
            row();
            rowAction("View batches");
            JDialog batches=awaitDialog("Medicine batches — Test medicine");
            waitFor(() -> table(batches)!=null && table(batches).getRowCount()==2);
            edt(() -> check("Batches for Test medicine".equals(
                table(batches).getAccessibleContext().getAccessibleName()),
                "batch detail table has an accessible name"));
            click(batches,"Close");
            waitFor(() -> !batches.isShowing());
            check(true,"grouped inventory exposes both underlying stock batches");
            printInventoryHistoryEvents();
            click(frame, "Reports");
            waitFor(()->showingTableModel("ConsultationsTableModel"));
            row();
            JTable reportTable=table();
            String reportRecordId=String.valueOf(reportTable.getValueAt(reportTable.getSelectedRow(),column(reportTable,"Record ID")));
            click(frame, "Generate selected report");
            JDialog report=awaitCopyDialog("School clinic","Record ID");
            setClipboardText("clipboard-marker");
            clickCopyForId(report,"Record ID");
            waitFor(()->reportRecordId.equals(clipboardTextUnchecked()));
            check(reportRecordId.equals(clipboardText()),"report detail Copy button copies only the Record ID");
            click(report,"OK");waitFor(()->!report.isShowing());
            check(true, "report dialog opens and closes");
            click(frame, "Nurse dashboard");
            waitFor(()->showingTableModel("RecentVisitsTableModel"));
            waitFor(() -> table() != null && table().getRowCount() == 3);
            check(visibleButton(frame,"+ New Consultation"),"Dashboard Recent Clinic Visits still shows + New Consultation");
            check(labelCount("Shared patient registry") == 1 && labelCount("Shared inventory") == 2,
                "nurse KPI captions identify shared registry and inventory data");
            edt(() -> frame.setSize(1280, 800));
            waitFor(() -> nurseMetricColumns() == 4);
            screenshot("desktop-nurse-1280");
            check(true, "nurse dashboard uses four KPI columns at desktop width");
            edt(() -> frame.setSize(820, 800));
            waitFor(() -> nurseMetricColumns() == 2);
            screenshot("desktop-nurse-820");
            check(true, "nurse dashboard reflows to two KPI columns at compact width");
            click(frame,"Sign out");
            waitFor(() -> button(frame,"Sign in").isShowing());
            edt(() -> { fill(frame,"User ID","root"); fill(frame,"Password","test-password"); });
            click(frame,"Sign in");
            waitFor(() -> app.login.isLoggedIn() && components(frame).stream()
                .anyMatch(c -> c instanceof JButton b && "Audit log".equals(b.getText())));
            var initialStock=app.admin.audit(0,100,"initial stock added").rows();
            var restockEvents=app.admin.audit(0,100,"restock new batch").rows();
            check(initialStock.stream().anyMatch(event -> "uic-main".equals(event.getCampusId())
                    && event.getDetails().contains("quantity=10")
                    && event.getDetails().contains("expiry=")),
                "initial stock audit records type, amount, expiry, and campus");
            check(restockEvents.stream().anyMatch(event -> "uic-main".equals(event.getCampusId())
                    && event.getDetails().contains("quantity=12")
                    && event.getDetails().contains("expiry=")),
                "restock audit records type, amount, expiry, and campus");
            LocalDate mergedExpiry=LocalDate.now().plusDays(90);
            app.login.logout();
            check(app.login.login("nurse", "test-password").isPresent(),"nurse signs in for initial stock merge");
            app.inventoryBatchSession("Test medicine",false,true).accept(Map.of(
                "Quantity","2","Expiry (YYYY-MM-DD)",mergedExpiry.toString(),"Remarks","initial stock merge"));
            app.login.logout();
            check(app.login.login("root", "test-password").isPresent(),"administrator signs in to inspect merge audit");
            var initialMerges=app.admin.audit(0,100,"initial stock merged").rows();
            check(initialMerges.stream().anyMatch(event -> "uic-main".equals(event.getCampusId())
                    &&event.getDetails().contains("quantity=2")
                    &&event.getDetails().contains("expiry="+mergedExpiry))
                    &&app.admin.audit(0,100,"restock merged").rows().isEmpty(),
                "merging initial stock stays distinct from a restock audit");
            edt(() -> frame.dispose());
            AtomicReference<GUI> actual = new AtomicReference<>();
            edt(() -> { actual.set(new GUI(new ApplicationContext(false),false,null)); actual.get().setVisible(true); });
            SwingUtilities.invokeLater(() -> actual.get().dispatchEvent(new WindowEvent(actual.get(), WindowEvent.WINDOW_CLOSING)));
            JDialog close = awaitDialog("Confirm");
            click(close, "Cancel");
            waitFor(() -> !close.isShowing());
            check(actual.get().isDisplayable(), "cancel close retains window");
            SwingUtilities.invokeLater(() -> actual.get().dispatchEvent(new WindowEvent(actual.get(), WindowEvent.WINDOW_CLOSING)));
            click(awaitDialog("Confirm"), "OK");
            waitFor(() -> !actual.get().isDisplayable());
            check(true, "application startup and confirmed shutdown");
            System.out.println("PASS: " + checks + " native Swing workflow checks.");
        } finally {
            edt(() -> { for (Window window : Window.getWindows()) window.dispose(); });
            if(app!=null)app.close();
        }
    }
}
