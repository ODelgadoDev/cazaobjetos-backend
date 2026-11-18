package mx.utch.cazaobjetos.dto;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
public record PushTestRequestDto(@NotNull UUID subscriptionId) {}
