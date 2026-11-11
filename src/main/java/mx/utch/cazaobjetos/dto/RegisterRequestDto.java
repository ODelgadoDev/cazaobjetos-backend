package mx.utch.cazaobjetos.dto;

import jakarta.validation.constraints.*;

public record RegisterRequestDto(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 120) String displayName) {
    @Override public String toString() { return "RegisterRequestDto[redacted]"; }
}
