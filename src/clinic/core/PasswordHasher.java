package clinic.core;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
/** One salted PBKDF2 derivation per password; no plaintext is retained in account storage. */
final class PasswordHasher {
    private static final int ITERATIONS=210000;
    private PasswordHasher() {
    }
    static String hash(String password) {
        byte[] salt=new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(derive(password, salt));
    }
    static boolean matches(String password, String stored) {
        if(stored==null)return false;
        String[] parts=stored.split(":");
        return parts.length==2&&MessageDigest.isEqual(derive(password, Base64.getDecoder().decode(parts[0])), Base64.getDecoder().decode(parts[1]));
    }
    private static byte[] derive(String password, byte[] salt) {
        char[] chars=password.toCharArray();
        PBEKeySpec spec=new PBEKeySpec(chars, salt, ITERATIONS, 256);
        Arrays.fill(chars, '\0');
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        }
        catch(GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing unavailable.", e);
        }
        finally {
            spec.clearPassword();
        }
    }
}
