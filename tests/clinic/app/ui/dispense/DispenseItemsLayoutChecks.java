package clinic.app.ui.dispense;

import clinic.app.ApplicationContext;
import clinic.app.Theme;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** Headless regression checks for growing and independently scrolling medicine rows. */
public final class DispenseItemsLayoutChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Theme.install();
        try(ApplicationContext app=new ApplicationContext(true)) {
            app.login.login("N001","nurse123").orElseThrow();
            DispenseItemsPanel[] holder=new DispenseItemsPanel[1];
            SwingUtilities.invokeAndWait(()->holder[0]=new DispenseItemsPanel(app,true));
            DispenseItemsPanel panel=holder[0];
            int oneRow=panel.rowsContentHeight();
            check(panel.itemCount()==1&&oneRow>0,"one medicine row has a measured height");
            addRows(panel,4);
            int fiveRows=panel.rowsContentHeight();
            check(panel.itemCount()==5&&fiveRows>oneRow,"medicine area grows through five rows");
            resizeAndLayout(panel.rowsScrollPane());
            check(panel.rowsScrollPane().getVerticalScrollBar().isVisible(),
                "five-row overflow scrolls inside the medicine area");
            int viewport=panel.rowsScrollPane().getPreferredSize().height;
            addRows(panel,7);
            check(panel.itemCount()==12&&panel.rowsContentHeight()>fiveRows,
                "medicine content continues growing at twelve rows");
            check(panel.rowsScrollPane().getPreferredSize().height==viewport,
                "medicine viewport stops growing at its height cap");
            resizeAndLayout(panel.rowsScrollPane());
            check(panel.rowsScrollPane().getVerticalScrollBar().isVisible(),
                "twelve rows retain an internal vertical scrollbar");
            int beforeRemoval=panel.rowsContentHeight();
            SwingUtilities.invokeAndWait(()->lastButton(panel,"Remove").doClick());
            check(panel.itemCount()==11&&panel.rowsContentHeight()<beforeRemoval,
                "removing a row shrinks the scroll content");
            JScrollPane outer=new JScrollPane(panel);
            clinic.app.ui.dashboard.WheelForwarder.install(outer);
            check(panel.getComponentCount()>0,"the shared dispense panel remains usable inside the popup scroll");
            System.out.println("PASS: "+checks+" medicine-row layout checks (1, 5, 12 rows).");
        }
    }

    private static void addRows(DispenseItemsPanel panel,int count)throws Exception {
        SwingUtilities.invokeAndWait(()->{
            for(int i=0;i<count;i++)button(panel,"+ Add medicine").doClick();
        });
        waitFor(()->panel.itemCount()>=count+1);
    }

    private static JButton button(Container root,String label) {
        for(Component child:root.getComponents()) {
            if(child instanceof JButton button&&label.equals(button.getText()))return button;
            if(child instanceof Container nested) {
                try{return button(nested,label);}catch(IllegalStateException ignored) { }
            }
        }
        throw new IllegalStateException("Button not found: "+label);
    }

    private static JButton lastButton(Container root,String label) {
        java.util.List<JButton> matches=new java.util.ArrayList<>();
        collectButtons(root,label,matches);
        if(matches.isEmpty())throw new IllegalStateException("Button not found: "+label);
        return matches.get(matches.size()-1);
    }

    private static void collectButtons(Container root,String label,java.util.List<JButton> matches) {
        for(Component child:root.getComponents()) {
            if(child instanceof JButton button&&label.equals(button.getText()))matches.add(button);
            if(child instanceof Container nested)collectButtons(nested,label,matches);
        }
    }

    private static void resizeAndLayout(JScrollPane scroll) {
        Dimension size=scroll.getPreferredSize();
        scroll.setSize(520,size.height);
        scroll.doLayout();
        scroll.getViewport().doLayout();
    }

    private static void waitFor(java.util.function.BooleanSupplier ready)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(System.nanoTime()<end) {
            AtomicBoolean result=new AtomicBoolean();
            SwingUtilities.invokeAndWait(()->result.set(ready.getAsBoolean()));
            if(result.get())return;
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for medicine rows.");
    }

    private static void check(boolean passed,String label) {
        if(!passed)throw new AssertionError(label);
        checks++;
        System.out.println("PASS: "+label);
    }
}
