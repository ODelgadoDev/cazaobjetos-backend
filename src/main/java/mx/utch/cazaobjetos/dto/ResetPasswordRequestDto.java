package mx.utch.cazaobjetos.dto;
import jakarta.validation.constraints.*;
public record ResetPasswordRequestDto(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,
        @NotBlank @Size(min=12,max=72) String newPassword) {
    @Override public String toString() { return "ResetPasswordRequestDto[redacted]"; }
}
