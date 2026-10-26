package clinic.app;

import java.util.*;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Session-scoped, trailing-edge event batching; no polling or application data retained. */
public final class DataChangeBus {
    private final Set<Subscription> subscribers=new LinkedHashSet<>();
    private long epoch;
    private boolean scheduled;
    public synchronized long epoch(){return epoch;}
    public Subscription subscribe(DataTopic topic,Runnable callback){return subscribe(callback,topic);}
    public synchronized Subscription subscribe(Runnable callback,DataTopic... topics) {
        Set<DataTopic> selected=topics.length==0?EnumSet.noneOf(DataTopic.class):EnumSet.copyOf(Arrays.asList(topics));
        Subscription subscription=new Subscription(callback,selected);
        subscribers.add(subscription);return subscription;
    }
    public void publish(DataTopic... topics) {
        long version;
        synchronized(this) {
            for(Subscription s:subscribers)for(DataTopic topic:topics)if(s.topics.contains(topic))s.pending=true;
            if(scheduled||subscribers.stream().noneMatch(s->s.pending))return;
            scheduled=true;version=epoch;
        }
        SwingUtilities.invokeLater(()->{
            Timer timer=new Timer(200,e->deliver(version));timer.setRepeats(false);timer.start();
        });
    }
    private void deliver(long version) {
        List<Subscription> ready;
        synchronized(this) {
            if(version!=epoch)return;
            scheduled=false;ready=subscribers.stream().filter(s->s.pending).toList();
            ready.forEach(s->s.pending=false);
        }
        for(Subscription s:ready)if(s.active()) {
            try{s.callback.run();}catch(RuntimeException error){
                java.util.logging.Logger.getLogger(DataChangeBus.class.getName()).warning("View refresh callback failed: "+error.getClass().getSimpleName());
            }
        }
    }
    public synchronized void clearSession(){epoch++;subscribers.forEach(s->s.closed=true);subscribers.clear();scheduled=false;}
    public synchronized int subscriberCount(){return subscribers.size();}
    public final class Subscription implements AutoCloseable {
        private final Runnable callback;
        private final Set<DataTopic> topics;
        private boolean pending,closed;
        private Subscription(Runnable callback,Set<DataTopic> topics){this.callback=Objects.requireNonNull(callback);this.topics=topics;}
        public boolean active(){synchronized(DataChangeBus.this){return !closed;}}
        public void close(){synchronized(DataChangeBus.this){closed=true;subscribers.remove(this);}}
    }
}
