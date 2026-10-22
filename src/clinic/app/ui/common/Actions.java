package clinic.app.ui.common;
import clinic.app.ui.common.popup.MessagePopup;
import javax.swing.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
public final class Actions {
    private Actions() {
    }
    public static <T> void run(JComponent owner, Callable<T> work, Consumer<T> success) {
        owner.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        AsyncLoader.load(work, result-> {
            owner.setCursor(java.awt.Cursor.getDefaultCursor());
            success.accept(result);
        }, error-> {
            owner.setCursor(java.awt.Cursor.getDefaultCursor());
            if(owner.isShowing())MessagePopup.error(owner, error);
        });
    }
}
