package mx.utch.cazaobjetos.dto;
import jakarta.validation.constraints.*;
public record ChangePasswordRequestDto(@NotBlank @Size(max=72) String currentPassword,
        @NotBlank @Size(min=12,max=72) String newPassword) {
    @Override public String toString() { return "ChangePasswordRequestDto[redacted]"; }
}
