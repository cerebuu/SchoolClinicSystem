package clinic.core;

import clinic.persistence.AccountRepository;
import clinic.persistence.Database;
import java.io.Console;
import java.nio.file.Path;
import java.util.Arrays;

/** One-time terminal recovery; never accepts a password in arguments or prints a hash. */
public final class ResetPassword {
    private ResetPassword() {}
    public static void main(String[] args) {
        if(args.length!=1||!validId(args[0])) {
            System.err.println("Usage: java -cp <classpath> clinic.core.ResetPassword <account-id>");
            System.exit(1);
            return;
        }
        Console console=System.console();
        if(console==null) {
            System.err.println("Run this utility in a terminal with a hidden password prompt.");
            System.exit(1);
            return;
        }
        char[] password=console.readPassword("New password: ");
        int status=0;
        try {
            validate(password);
            try(Database database=Database.fromEnvironment(Path.of(System.getProperty("clinic.database","data/scms")))) {
                reset(database,args[0],password);
            }
            System.out.println("Password reset for "+args[0]+".");
        } catch(IllegalArgumentException failure) {
            System.err.println("Password reset failed. Check the account ID and use at least eight characters.");
            status=1;
        } catch(RuntimeException failure) {
            System.err.println("Password reset failed. Check database configuration and availability.");
            status=1;
        } finally {
            if(password!=null)Arrays.fill(password,'\0');
        }
        if(status!=0)System.exit(status);
    }
    static void reset(Database database,String id,char[] password) {
        if(!validId(id))throw new IllegalArgumentException("Invalid account ID.");
        validate(password);
        new AccountRepository(database).resetPassword(id,PasswordHasher.hash(new String(password)));
    }
    private static void validate(char[] password) {
        if(password==null||!Login.passwordMeetsMinimum(new String(password)))
            throw new IllegalArgumentException("Password must contain at least eight characters.");
    }
    private static boolean validId(String id) {
        return id!=null&&!id.isBlank()&&id.equals(id.trim())&&id.codePoints().noneMatch(Character::isISOControl);
    }
}
