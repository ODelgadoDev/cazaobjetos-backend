package mx.utch.cazaobjetos.exception;

import org.springframework.http.HttpStatus;

// Unchecked so transactional business failures always roll back.
public class BusinessException extends RuntimeException {
    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatusCode() { return status; }
}
