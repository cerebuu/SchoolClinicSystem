package clinic.app.ui.common;

import clinic.app.*;
import java.awt.event.HierarchyEvent;
import javax.swing.JComponent;

/** Ties a subscription to a displayable component; hidden views only become dirty. */
public final class LiveRefresh {
    private final JComponent owner;
    private final DataChangeBus bus;
    private final Runnable refresh;
    private final DataTopic[] topics;
    private final long epoch;
    private DataChangeBus.Subscription subscription;
    private boolean dirty=true;
    public LiveRefresh(JComponent owner,DataChangeBus bus,Runnable refresh,DataTopic... topics) {
        this.owner=owner;this.bus=bus;this.refresh=refresh;this.topics=topics.clone();epoch=bus.epoch();
        owner.addHierarchyListener(e->{if((e.getChangeFlags()&(HierarchyEvent.DISPLAYABILITY_CHANGED|HierarchyEvent.SHOWING_CHANGED))!=0)visibility();});
        visibility();
    }
    public boolean active(){return epoch==bus.epoch()&&owner.isDisplayable();}
    private void visibility() {
        if(!owner.isDisplayable()||epoch!=bus.epoch()) {
            if(subscription!=null)subscription.close();subscription=null;dirty=true;return;
        }
        if(subscription==null)subscription=bus.subscribe(()->{dirty=true;update();},topics);
        update();
    }
    private void update(){if(dirty&&owner.isShowing()&&active()){dirty=false;refresh.run();}}
}
