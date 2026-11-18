package mx.utch.cazaobjetos.exception.handler;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import mx.utch.cazaobjetos.dto.ErrorResponseDto;
import mx.utch.cazaobjetos.exception.BusinessException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDto> tooLarge(Exception exception) {
        return ResponseEntity.status(413).body(new ErrorResponseDto(413,"La carga excede el límite: 5 MB por archivo y 26 MB por solicitud"));
    }
    @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponseDto> missingFile(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponseDto(400,"Falta una parte requerida del formulario o un parámetro de la solicitud"));
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> unreadable(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponseDto(400, "Revisa el formato y los campos permitidos de la solicitud"));
    }
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDto> business(BusinessException exception) {
        return ResponseEntity.status(exception.getStatusCode())
            .body(new ErrorResponseDto(exception.getStatusCode().value(), exception.getMessage()));
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> conflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponseDto(409, "La operación entra en conflicto con un registro existente"));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> invalid(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponseDto(400, "Revisa los campos, filtros, fechas y paginación"));
    }
    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class,
            jakarta.validation.ConstraintViolationException.class})
    public ResponseEntity<ErrorResponseDto> invalidQuery(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponseDto(400, "Parámetros de consulta inválidos"));
    }
}
