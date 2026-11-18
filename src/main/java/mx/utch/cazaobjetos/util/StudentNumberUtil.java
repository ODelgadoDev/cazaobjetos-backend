package mx.utch.cazaobjetos.util;

import java.util.regex.Pattern;
import mx.utch.cazaobjetos.enums.UserRole;

public final class StudentNumberUtil {
    // Recognizes the example supplied for this MVP; it does not validate enrollment.
    private static final Pattern EMAIL = Pattern.compile("^a([0-9]{10})@utch\\.edu\\.mx$");
    private StudentNumberUtil() {}

    public static String fromEmail(String email, UserRole role) {
        if (role != UserRole.STUDENT || email == null) return null;
        var match = EMAIL.matcher(email);
        return match.matches() ? match.group(1) : null;
    }
}
