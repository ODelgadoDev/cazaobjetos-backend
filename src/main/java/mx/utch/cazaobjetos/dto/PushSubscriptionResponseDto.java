package mx.utch.cazaobjetos.dto;
import java.util.UUID;
public record PushSubscriptionResponseDto(UUID id,UUID userId,String bindingToken) {}
