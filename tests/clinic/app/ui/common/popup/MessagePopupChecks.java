package clinic.app.ui.common.popup;

import clinic.app.ui.common.ClipboardSupport;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Headless checks for exact detail text copying and shared popup actions. */
public final class MessagePopupChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Clipboard clipboard=new Clipboard("message-popup-checks");
        java.util.function.Consumer<String> copy=value->
            clipboard.setContents(new StringSelection(value),null);
        String report="========================================\nSCHOOL CLINIC REPORT\n"
            +"Generated: 2026-10-29 10:11\n========================================\n"
            +"record-1 | patient-1 | 2026-10-29 08:26\nCampus: uic-main\n"
            +"Symptoms: Fever, Cough\nConcern: qe\nMedicines administered: None\nRemarks: qe\n"
            +"========================================\nEnd of report\n========================================";
        Map<String,String> ids=new LinkedHashMap<>();
        ids.put("Patient ID","patient-1");
        ids.put("Record ID","record-1");
        JPanel content=MessagePopup.detailsContent(report,ids,copy);
        JPanel actions=MessagePopup.detailsActions("Copy report",report,ids,true,copy,()->{});
        JTextArea text=find(content,JTextArea.class);
        check(text!=null&&report.equals(text.getText())&&!text.isEditable(),
            "report text is shown exactly and remains read-only");
        JScrollPane scroll=find(content,JScrollPane.class);
        check(scroll!=null&&scroll.getVerticalScrollBarPolicy()==JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
            &&scroll.isWheelScrollingEnabled(),"report has a visible wheel-enabled scrollbar");
        click(actions,"copy:report");
        check(report.equals(readClipboard(clipboard)),"Copy report copies the entire report verbatim");
        check("Copied".equals(button(actions,"copy:report").getText()),
            "Copy report shows immediate Copied feedback");
        click(content,"copy:Patient ID");
        check("patient-1".equals(readClipboard(clipboard)),"Patient ID Copy button still copies the ID");
        check("Copied".equals(button(content,"copy:Patient ID").getText()),
            "ID Copy button shows Copied feedback");
        click(content,"copy:Record ID");
        check("record-1".equals(readClipboard(clipboard)),"Record ID Copy button still copies the ID");
        String all="Patient ID: patient-1\nRecord ID: record-1\n\n"+report;
        click(actions,"copy:all");
        check(all.equals(readClipboard(clipboard)),"Copy all includes both IDs and the report");
        check("Copied".equals(button(actions,"copy:all").getText()),
            "Copy all shows Copied feedback");
        waitForReset(button(actions,"copy:report"),"Copy report");
        waitForReset(button(content,"copy:Patient ID"),"Copy");
        AtomicBoolean closed=new AtomicBoolean();
        JPanel closable=MessagePopup.detailsActions("Copy",report,Map.of(),false,copy,
            ()->closed.set(true));
        click(closable,"close:details");
        check(closed.get(),"Close action can close the detail popup");
        if(!GraphicsEnvironment.isHeadless())popupOpenClose(report,ids);
        System.out.println("PASS: "+checks+" message popup copy checks.");
    }

    private static void popupOpenClose(String report,Map<String,String> ids) throws Exception {
        ClipboardSupport.install();
        JPanel owner=new JPanel();
        SwingUtilities.invokeLater(()->MessagePopup.infoWithCopies(owner,
            MessagePopup.CONSULTATION_RECORD_TITLE,report,ids,true));
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        JDialog dialog=null;
        while(System.nanoTime()<deadline&&dialog==null) {
            dialog=Arrays.stream(Window.getWindows()).filter(window->window instanceof JDialog candidate
                &&candidate.isShowing()&&MessagePopup.CONSULTATION_RECORD_TITLE.equals(candidate.getTitle()))
                .map(window->(JDialog)window).findFirst().orElse(null);
            if(dialog==null)Thread.sleep(20);
        }
        check(dialog!=null,"consultation record popup opens");
        JDialog opened=dialog;
        JTextArea reportArea=components(opened).stream().filter(component->component instanceof JTextArea area
            &&"report-text".equals(area.getName())).map(component->(JTextArea)component)
            .findFirst().orElseThrow();
        SwingUtilities.invokeAndWait(()->{
            reportArea.selectAll();
            KeyStroke shortcut=KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
            Object action=reportArea.getInputMap(JComponent.WHEN_FOCUSED).get(shortcut);
            if(action!=null)reportArea.getActionMap().get(action).actionPerformed(
                new java.awt.event.ActionEvent(reportArea,0,"copy"));
        });
        check(report.equals((String)Toolkit.getDefaultToolkit().getSystemClipboard()
            .getData(DataFlavor.stringFlavor)),"Cmd+C copies selected read-only report text");
        JButton close=components(opened).stream().filter(component->component instanceof JButton candidate
            &&"close:details".equals(candidate.getName())).map(component->(JButton)component)
            .findFirst().orElseThrow();
        SwingUtilities.invokeAndWait(close::doClick);
        check(!opened.isShowing(),"consultation record popup closes without errors");
    }

    private static String readClipboard(Clipboard clipboard) throws Exception {
        return (String)clipboard.getData(DataFlavor.stringFlavor);
    }
    private static void waitForReset(JButton button,String label) throws Exception {
        Thread.sleep(1600);
        AtomicBoolean reset=new AtomicBoolean();
        SwingUtilities.invokeAndWait(()->reset.set(label.equals(button.getText())));
        check(reset.get(),label+" feedback resets after 1.5 seconds");
    }
    private static void click(java.awt.Container root,String name) throws Exception {
        SwingUtilities.invokeAndWait(()->button(root,name).doClick());
    }
    private static JButton button(java.awt.Container root,String name) {
        return components(root).stream().filter(component->component instanceof JButton candidate
            &&name.equals(candidate.getName())).map(component->(JButton)component).findFirst().orElseThrow();
    }
    private static <T> T find(java.awt.Container root,Class<T> type) {
        return components(root).stream().filter(type::isInstance).map(type::cast).findFirst().orElse(null);
    }
    private static java.util.List<java.awt.Component> components(java.awt.Container root) {
        java.util.List<java.awt.Component> result=new java.util.ArrayList<>();
        for(java.awt.Component component:root.getComponents()) {
            result.add(component);
            if(component instanceof java.awt.Container container)result.addAll(components(container));
        }
        return result;
    }
    private static void check(boolean passed,String description) {
        if(!passed)throw new AssertionError(description);
        checks++;
    }
}
