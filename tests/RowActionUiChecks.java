import clinic.app.ui.common.PagedTableModel;
import clinic.app.ui.common.PagedTablePanel;
import clinic.app.ui.common.RowAction;
import clinic.service.PageResult;
import java.awt.Component;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.InputMap;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;

/** Headless structural and keyboard-access check for paged-table row action menus. */
public final class RowActionUiChecks {
    private static int checks;

    private RowActionUiChecks() {}

    public static void main(String[] args) throws Exception {
        AtomicReference<PagedTablePanel<String>> panelRef=new AtomicReference<>();
        AtomicReference<JTable> tableRef=new AtomicReference<>();
        AtomicInteger fetches=new AtomicInteger();
        AtomicInteger actionsRun=new AtomicInteger();
        CountDownLatch refreshStarted=new CountDownLatch(1);
        CountDownLatch finishRefresh=new CountDownLatch(1);
        SwingUtilities.invokeAndWait(()->{
            PagedTablePanel<String> panel=new PagedTablePanel<>(new Model(),
                (page,size,query)->{
                    if(fetches.incrementAndGet()>1) {
                        refreshStarted.countDown();
                        if(!finishRefresh.await(5,TimeUnit.SECONDS))
                            throw new IllegalStateException("Timed out waiting to finish the test refresh.");
                    }
                    return new PageResult<>(List.of("Patient p-1"),1,page,size);
                },"Search patient");
            panel.setRowActions(row->List.of(new RowAction("View details",actionsRun::incrementAndGet)));
            panel.reload();
            panelRef.set(panel);
            tableRef.set(findTable(panel));
        });

        long deadline=System.nanoTime()+5_000_000_000L;
        boolean loaded=false;
        while(System.nanoTime()<deadline&&!loaded) {
            AtomicBoolean rowsReady=new AtomicBoolean();
            SwingUtilities.invokeAndWait(()->rowsReady.set(tableRef.get().getRowCount()==1));
            loaded=rowsReady.get();
            if(!loaded)Thread.sleep(10);
        }
        check(loaded,"sample row loads into the paged table");

        SwingUtilities.invokeAndWait(()->{
            JTable table=tableRef.get();
            int actionColumn=table.getColumnCount()-1;
            check("Actions".equals(table.getColumnName(actionColumn)),"row actions column is present");
            check("Actions".equals(String.valueOf(table.getValueAt(0,actionColumn))),
                "row action cell has a readable value");
            check(table.getColumnModel().getColumn(actionColumn).getMaxWidth()<=64,
                "row actions column remains compact");
            check(table.getAccessibleContext().getAccessibleDescription().contains("Shift+F10"),
                "table explains its keyboard action shortcut");
            InputMap keys=table.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            check("open-row-actions".equals(keys.get(KeyStroke.getKeyStroke("shift F10"))),
                "Shift+F10 opens row actions");
        });

        SwingUtilities.invokeAndWait(()->{
            tableRef.get().setRowSelectionInterval(0,0);
            check("Patient p-1".equals(panelRef.get().selected()),"loaded row can be selected");
            panelRef.get().reload();
        });
        check(refreshStarted.await(5,TimeUnit.SECONDS),"refresh reaches its controlled loading state");
        SwingUtilities.invokeAndWait(()->{
            JTable table=tableRef.get();
            check(!table.isEnabled(),"table is disabled while refreshed rows are loading");
            check(panelRef.get().selected()==null,"selected row is unavailable during refresh");
            table.getActionMap().get("open-row-actions").actionPerformed(
                new ActionEvent(table,ActionEvent.ACTION_PERFORMED,"test"));
            check(actionsRun.get()==0,"keyboard row action cannot run while refresh is loading");
        });
        finishRefresh.countDown();
        long refreshDeadline=System.nanoTime()+5_000_000_000L;
        boolean refreshed=false;
        while(System.nanoTime()<refreshDeadline&&!refreshed) {
            AtomicBoolean enabled=new AtomicBoolean();
            SwingUtilities.invokeAndWait(()->enabled.set(tableRef.get().isEnabled()));
            refreshed=enabled.get();
            if(!refreshed)Thread.sleep(10);
        }
        check(refreshed,"table is re-enabled after refresh completes");
        SwingUtilities.invokeAndWait(()->check("Patient p-1".equals(panelRef.get().selected()),
            "selection is preserved after refresh completes"));
        panelRef.get().removeNotify();
        System.out.println("PASS: "+checks+" row action UI checks.");
    }

    private static JTable findTable(java.awt.Container parent) {
        for(Component child:parent.getComponents()) {
            if(child instanceof JTable table)return table;
            if(child instanceof java.awt.Container container) {
                JTable found=findTable(container);
                if(found!=null)return found;
            }
        }
        return null;
    }

    private static void check(boolean passed,String description) {
        if(!passed)throw new AssertionError(description);
        checks++;
    }

    private static final class Model extends PagedTableModel<String> {
        private Model(){super("Patient");}
        @Override protected Object value(String row,int column){return row;}
    }
}
