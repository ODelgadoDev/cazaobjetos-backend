package mx.utch.cazaobjetos.util;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;

public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    private static final Pattern DIGIT = Pattern.compile("\\p{Nd}");
    private static final Pattern SYMBOL = Pattern.compile("[\\p{P}\\p{S}]");

    private PasswordPolicy() {}

    public static void validate(String value) {
        require(value != null && value.codePointCount(0, value.length()) >= MIN_LENGTH,
                HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 8 caracteres");
        require(DIGIT.matcher(value).find() && SYMBOL.matcher(value).find(),
                HttpStatus.BAD_REQUEST, "La contraseña debe incluir al menos un número y un símbolo");
        require(value.getBytes(StandardCharsets.UTF_8).length <= 72,
                HttpStatus.BAD_REQUEST, "La contraseña no puede superar 72 bytes UTF-8");
    }
}
