package mx.utch.cazaobjetos.dto;

import jakarta.validation.constraints.*;

public record VerifyEmailRequestDto(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {
    @Override public String toString() { return "VerifyEmailRequestDto[redacted]"; }
}
