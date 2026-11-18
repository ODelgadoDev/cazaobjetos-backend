package mx.utch.cazaobjetos.dto;
import jakarta.validation.constraints.*;
public record ClaimCommentRequestDto(@NotBlank @Size(max=2000) String body) {}
