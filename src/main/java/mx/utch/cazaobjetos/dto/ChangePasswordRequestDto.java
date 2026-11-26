package mx.utch.cazaobjetos.dto;
import jakarta.validation.constraints.*;
import mx.utch.cazaobjetos.util.PasswordPolicy;
public record ChangePasswordRequestDto(@NotBlank @Size(max=72) String currentPassword,
        @NotBlank @Size(min=PasswordPolicy.MIN_LENGTH,max=72) String newPassword) {
    @Override public String toString() { return "ChangePasswordRequestDto[redacted]"; }
}
