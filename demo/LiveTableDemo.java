import clinic.app.*;
import clinic.app.ui.common.*;
import clinic.service.PageResult;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.*;
import javax.swing.*;

/** Paging, coalescing, hidden tabs, retry and unsubscribe checks on 10,000 synthetic rows. */
public final class LiveTableDemo {
    public static void main(String[] args)throws Exception {
        DataChangeBus bus=new DataChangeBus();AtomicInteger fetches=new AtomicInteger(),limit=new AtomicInteger(10000);
        AtomicBoolean fail=new AtomicBoolean();JFrame[] frame={null};JPanel cards=new JPanel(new CardLayout());
        AtomicReference<PagedTablePanel<String>> reference=new AtomicReference<>();
        try {
            LiveRefreshDemo.edt(()->{
                Theme.install();frame[0]=new JFrame("10,000-row event refresh");
                PagedTablePanel<String> table=new PagedTablePanel<>(new LiveTableModel(),(page,size,q)->{
                    fetches.incrementAndGet();if(size!=25)throw new AssertionError("Unbounded page request");
                    if(fail.get())throw new IllegalStateException("Synthetic refresh failure");
                    int count=limit.get(),start=Math.min(count,page*size),end=Math.min(count,start+size);
                    return new PageResult<>(java.util.stream.IntStream.range(start,end).mapToObj(i->q+" row "+i).toList(),count,page,size);
                });
                reference.set(table);table.refreshOn(bus,DataTopic.PATIENTS,DataTopic.VISITS);
                cards.add(table,"table");cards.add(new JPanel(),"hidden");frame[0].add(cards);frame[0].setSize(800,500);frame[0].setVisible(true);
            });
            var table=reference.get();LiveRefreshDemo.await(()->table.totalCount()==10000);
            LiveRefreshDemo.edt(()->table.applyFilter("filter kept"));LiveRefreshDemo.await(()->LiveRefreshDemo.grid(table).getValueAt(0,0).equals("filter kept row 0"));
            LiveRefreshDemo.edt(()->table.setPage(2));LiveRefreshDemo.await(()->LiveRefreshDemo.grid(table).getValueAt(0,0).equals("filter kept row 50"));
            LiveRefreshDemo.edt(()->LiveRefreshDemo.grid(table).setRowSelectionInterval(3,3));
            int before=fetches.get();for(int i=0;i<20;i++)bus.publish(DataTopic.PATIENTS,DataTopic.VISITS);
            LiveRefreshDemo.await(()->fetches.get()>before);Thread.sleep(300);
            LiveRefreshDemo.edt(()->{
                LiveRefreshDemo.check("Page 3 and filter preserved",table.currentPage()==2&&table.filterText().equals("filter kept"));
                LiveRefreshDemo.check("Selected row preserved",LiveRefreshDemo.grid(table).getSelectedRow()==3);
                System.out.println("10,000 rows: page="+(table.currentPage()+1)+", filter="+table.filterText()+", visible rows="+LiveRefreshDemo.grid(table).getRowCount()+", refreshes for 20 publishes="+(fetches.get()-before));
                LiveRefreshDemo.check("One refresh for multi-topic burst",fetches.get()-before==1);
                ((CardLayout)cards.getLayout()).show(cards,"hidden");
            });
            int hidden=fetches.get();bus.publish(DataTopic.PATIENTS);Thread.sleep(350);
            LiveRefreshDemo.check("Hidden tab performs no query",fetches.get()==hidden);
            LiveRefreshDemo.edt(()->((CardLayout)cards.getLayout()).show(cards,"table"));
            LiveRefreshDemo.await(()->fetches.get()==hidden+1);Thread.sleep(250);
            LiveRefreshDemo.check("Hidden dirty tab fetches once on open",fetches.get()==hidden+1);
            fail.set(true);bus.publish(DataTopic.PATIENTS);
            LiveRefreshDemo.await(()->LiveRefreshDemo.all(table).stream().anyMatch(c->c instanceof JLabel l&&l.getText().equals("Refresh failed")));
            fail.set(false);LiveRefreshDemo.edt(table::reload);LiveRefreshDemo.await(()->table.totalCount()==10000);
            LiveRefreshDemo.check("Refresh failure recovers via manual retry",true);
            limit.set(30);bus.publish(DataTopic.PATIENTS);LiveRefreshDemo.await(()->table.totalCount()==30&&table.currentPage()==1);
            LiveRefreshDemo.check("Deleted last page clamps to final existing page",true);
            AtomicBoolean onEdt=new AtomicBoolean();var handle=bus.subscribe(DataTopic.AUDIT,()->onEdt.set(SwingUtilities.isEventDispatchThread()));
            new Thread(()->bus.publish(DataTopic.AUDIT)).start();LiveRefreshDemo.await(onEdt::get);handle.close();
            LiveRefreshDemo.check("Background publish delivers on EDT",onEdt.get());
            LiveRefreshDemo.edt(()->frame[0].dispose());LiveRefreshDemo.check("Dispose unsubscribes",bus.subscriberCount()==0);
            System.out.println("PASS: table state, lifecycle, failure recovery and 10,000-row checks.");
        }finally{LiveRefreshDemo.edt(()->{if(frame[0]!=null)frame[0].dispose();});}
    }
}
