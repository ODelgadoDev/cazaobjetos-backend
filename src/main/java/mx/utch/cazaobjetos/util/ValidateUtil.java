package mx.utch.cazaobjetos.util;

import org.springframework.http.HttpStatus;
import mx.utch.cazaobjetos.exception.BusinessException;

public final class ValidateUtil {
    private ValidateUtil() {}

    public static void require(boolean condition, HttpStatus status, String message) {
        if (!condition) throw new BusinessException(status, message);
    }
}
