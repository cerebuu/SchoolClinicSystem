package clinic.app.ui.common;
import clinic.core.Login;
import clinic.model.User;
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

    /** Deliver a result only to the visible panel and login session that started the request. */
    public static <T> void runInSession(JComponent owner, Login login, Callable<T> work,
            Consumer<T> success) {
        User sessionUser;
        long sessionVersion;
        synchronized(login) {
            sessionUser=login.getCurrentUser().orElseThrow(()->new SecurityException("Sign in required."));
            sessionVersion=login.getSessionVersion();
        }
        owner.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        AsyncLoader.load(work,result->{
            owner.setCursor(java.awt.Cursor.getDefaultCursor());
            if(owner.isShowing()&&isCurrentSession(login,sessionVersion,sessionUser))success.accept(result);
        },error->{
            owner.setCursor(java.awt.Cursor.getDefaultCursor());
            if(owner.isShowing()&&isCurrentSession(login,sessionVersion,sessionUser))MessagePopup.error(owner,error);
        });
    }

    public static boolean isCurrentSession(Login login,long version,User sessionUser) {
        synchronized(login) {
            return login.getSessionVersion()==version
                &&login.getCurrentUser().orElse(null)==sessionUser;
        }
    }
}
