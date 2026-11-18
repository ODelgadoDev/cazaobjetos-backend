package mx.utch.cazaobjetos.dto;
import jakarta.validation.constraints.*;
public record PushKeysDto(@NotBlank @Size(max=100) String p256dh,@NotBlank @Size(max=30) String auth) {}
