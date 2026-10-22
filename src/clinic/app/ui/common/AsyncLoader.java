package clinic.app.ui.common;
import javax.swing.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
public final class AsyncLoader {
    private AsyncLoader() {
    }
    public static <T> SwingWorker<T, Void> load(Callable<T> task, Consumer<T> success, Consumer<Throwable> failure) {
        SwingWorker<T, Void> worker=new SwingWorker<>() {
            protected T doInBackground() throws Exception {
                return task.call();
            }
            protected void done() {
                if(isCancelled())return;
                try {
                    success.accept(get());
                }
                catch(Exception e) {
                    failure.accept(e.getCause()==null?e:e.getCause());
                }
            }
        };
        worker.execute();
        return worker;
    }
}
