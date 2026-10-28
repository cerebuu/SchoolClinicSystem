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
        if(password==null||stored==null)return false;
        try {
            String[] parts=stored.split(":",-1);
            if(parts.length==2)return MessageDigest.isEqual(derive(password, Base64.getDecoder().decode(parts[0])),
                Base64.getDecoder().decode(parts[1]));
            if(parts.length!=3||!parts[0].matches("[0-9]{1,7}")||!parts[2].matches("[0-9a-fA-F]{64}"))return false;
            int iterations=Integer.parseInt(parts[0]);
            if(iterations<1||iterations>1000000||parts[1].isEmpty()||parts[1].length()>512)return false;
            byte[] expected=HexFormat.of().parseHex(parts[2]);
            // Legacy salt fields may represent literal text or hex-encoded salt bytes.
            boolean literal=MessageDigest.isEqual(derive(password,parts[1].getBytes(java.nio.charset.StandardCharsets.UTF_8),iterations),expected);
            boolean encoded=false;
            if(parts[1].matches("(?:[0-9a-fA-F]{2})+"))
                encoded=MessageDigest.isEqual(derive(password,HexFormat.of().parseHex(parts[1]),iterations),expected);
            return literal|encoded;
        } catch(IllegalArgumentException malformed) { return false; }
    }
    private static byte[] derive(String password, byte[] salt) {
        return derive(password,salt,ITERATIONS);
    }
    private static byte[] derive(String password, byte[] salt,int iterations) {
        char[] chars=password.toCharArray();
        PBEKeySpec spec=new PBEKeySpec(chars, salt, iterations, 256);
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
