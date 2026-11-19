package mx.utch.cazaobjetos.dto;
import java.util.UUID;
import java.time.Instant;
import mx.utch.cazaobjetos.enums.ClaimActivityKind;
public record ClaimActivityDto(UUID id, ClaimActivityKind kind, UUID actorId, String actorName, String body, String detailsJson, UUID evidenceId, Instant createdAt, UUID actorAvatarVersion, String actorDegree) {}
