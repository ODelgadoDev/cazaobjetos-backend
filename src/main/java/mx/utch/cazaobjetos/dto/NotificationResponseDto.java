package mx.utch.cazaobjetos.dto;
import java.time.Instant;
import java.util.UUID;
import mx.utch.cazaobjetos.enums.*;
public record NotificationResponseDto(UUID id, NotificationKind kind, NotificationTarget targetType, UUID targetId, String title, String body, Instant createdAt, Instant readAt) {}
