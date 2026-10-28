package clinic.app.ui.common;

import clinic.core.Login;
import clinic.model.Administrator;
import clinic.model.User;

/** Session ownership checks for asynchronous UI completion callbacks. */
public final class ActionsSessionChecks {
    private static int checks;

    public static void main(String[] args) {
        Login login=new Login();
        login.bootstrapAdministrator("first","First Admin","first-password");
        User first=login.login("first","first-password").orElseThrow();
        long firstVersion=login.getSessionVersion();
        login.addUser(new Administrator("second","Second Admin"),"second-password");
        check(Actions.isCurrentSession(login,firstVersion,first),"current session accepts its own completion");

        login.logout();
        check(!Actions.isCurrentSession(login,firstVersion,first),"logout invalidates pending completions");

        User firstAgain=login.login("first","first-password").orElseThrow();
        long nextVersion=login.getSessionVersion();
        User second=login.login("second","second-password").orElseThrow();
        check(!Actions.isCurrentSession(login,nextVersion,firstAgain),"a different user cannot receive prior-session results");
        check(Actions.isCurrentSession(login,login.getSessionVersion(),second),"new user owns the new session");

        long beforeFailedLogin=login.getSessionVersion();
        check(login.login("second","incorrect").isEmpty(),"failed reauthentication is rejected");
        check(!Actions.isCurrentSession(login,beforeFailedLogin,second),"failed reauthentication also invalidates pending results");
        System.out.println("PASS: "+checks+" async session ownership checks.");
    }

    private static void check(boolean passed,String message) {
        if(!passed)throw new AssertionError(message);
        checks++;
    }
}
