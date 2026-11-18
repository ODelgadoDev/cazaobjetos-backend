package mx.utch.cazaobjetos.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record PushSubscriptionRequestDto(@NotNull java.util.UUID expectedUserId,@NotBlank @Size(max=2048) String endpoint,@NotNull @Valid PushKeysDto keys) {}
