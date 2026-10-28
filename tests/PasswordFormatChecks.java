package clinic.core;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Synthetic password fixtures only; no account hashes or credentials are printed. */
public final class PasswordFormatChecks {
    public static void main(String[] args) throws Exception {
        String password="fixture-password",salt="a1b2c3d4e5";
        String modern=PasswordHasher.hash(password);
        check(PasswordHasher.matches(password,modern),"current H2 hash");
        check(!PasswordHasher.matches("wrong",modern),"current wrong password");
        for(byte[] bytes:new byte[][]{salt.getBytes(StandardCharsets.UTF_8),HexFormat.of().parseHex(salt)}) {
            byte[] hash=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec(password.toCharArray(),bytes,1000,256)).getEncoded();
            String legacy="1000:"+salt+":"+HexFormat.of().formatHex(hash);
            check(PasswordHasher.matches(password,legacy),"legacy hash");
            check(!PasswordHasher.matches("wrong",legacy),"legacy wrong password");
        }
        for(String malformed:new String[]{"",":", "abc:abc", "0:s:abc","9999999:s:"+"a".repeat(64),"1000::"+"a".repeat(64)})
            check(!PasswordHasher.matches(password,malformed),"malformed hash");
        System.out.println("PASS: 12 password-format checks (current Base64, legacy literal/hex salts, wrong passwords, malformed input). No rehash.");
    }
    private static void check(boolean result,String label) { if(!result)throw new AssertionError(label); }
}
