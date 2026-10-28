package clinic.core;

import clinic.model.Administrator;
import clinic.persistence.*;
import java.nio.file.Files;

/** Isolated recovery fixture; never connects to a configured remote database. */
public final class ResetPasswordChecks {
    public static void main(String[] args) throws Exception {
        try(Database database=new Database(Files.createTempDirectory("scms-reset-").resolve("clinic"))) {
            AccountRepository accounts=new AccountRepository(database);
            accounts.insert(new Administrator("target","Administrator"),PasswordHasher.hash("old-fixture"));
            accounts.insert(new Administrator("other","Other"),PasswordHasher.hash("other-fixture"));
            accounts.attempts("target",3,123456789);
            var inactive=new Administrator("target","Administrator");
            inactive.setCampusId("unchanged-campus");accounts.update(inactive,false);
            var other=accounts.load().get(0);
            ResetPassword.reset(database,"target","new-fixture-password".toCharArray());
            var target=accounts.load().stream().filter(a->a.user().getUserId().equals("target")).findFirst().orElseThrow();
            check(PasswordHasher.matches("new-fixture-password",target.hash()),"login hasher verifies new password");
            check(!PasswordHasher.matches("old-fixture",target.hash()),"old password rejected");
            check(target.failures()==0&&target.lockedUntil()==0,"lockout reset");
            check(!target.active()&&target.user().getRole().equals("ADMIN")&&target.user().getCampusId().equals("unchanged-campus"),"other fields preserved");
            check(accounts.load().get(0).hash().equals(other.hash()),"other account preserved");
            String hash=target.hash();
            for(String id:new String[]{"missing","target"}) {
                try {
                    ResetPassword.reset(database,id,(id.equals("target")?"short":"valid-fixture").toCharArray());
                    throw new AssertionError("Invalid reset accepted");
                } catch(IllegalArgumentException expected) { /* Unknown accounts and short passwords fail. */ }
            }
            check(accounts.load().stream().filter(a->a.user().getUserId().equals("target")).findFirst().orElseThrow().hash().equals(hash),"failed reset changes nothing");
            System.out.println("PASS: password recovery reuses login hashing, clears lockout, preserves other fields/accounts, and rejects invalid resets.");
        }
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
