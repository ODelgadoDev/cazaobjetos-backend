package mx.utch.cazaobjetos.dto;
import java.util.UUID;
import java.time.Instant;
import mx.utch.cazaobjetos.enums.ClaimActivityKind;
public record ClaimEvidenceDto(UUID id, UUID authorId, String contentType, int sizeBytes, Instant createdAt) {}
