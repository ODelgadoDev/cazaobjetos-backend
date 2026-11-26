package mx.utch.cazaobjetos.dto;

import jakarta.validation.constraints.*;
import mx.utch.cazaobjetos.util.PasswordPolicy;

public record RegisterRequestDto(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = 72) String password,
        @NotBlank @Size(min = 3, max = 120) String displayName,
        @NotBlank(message="Escribe tu carrera") @Size(max = 160) String degree,
        @Size(max = 80) String building) {
    @Override public String toString() { return "RegisterRequestDto[redacted]"; }
}
