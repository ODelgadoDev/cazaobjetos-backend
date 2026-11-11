package mx.utch.cazaobjetos.util;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public final class VerificationTokenUtil {
    private static final SecureRandom RANDOM = new SecureRandom();
    private VerificationTokenUtil() {}
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no disponible", impossible);
        }
    }
}
