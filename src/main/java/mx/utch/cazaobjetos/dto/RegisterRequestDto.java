package mx.utch.cazaobjetos.dto;

import jakarta.validation.constraints.*;

public record RegisterRequestDto(
        @NotBlank @Email @Size(max = 254)
        @Pattern(regexp="(?i)^a[0-9]{10}@utch\\.edu\\.mx$", message="Usa a + 10 dígitos + @utch.edu.mx") String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(min = 3, max = 120) String displayName,
        @NotBlank(message="Escribe tu carrera") @Size(max = 160) String degree,
        @NotBlank(message="Escribe tu edificio") @Size(max = 80) String building) {
    @Override public String toString() { return "RegisterRequestDto[redacted]"; }
}
