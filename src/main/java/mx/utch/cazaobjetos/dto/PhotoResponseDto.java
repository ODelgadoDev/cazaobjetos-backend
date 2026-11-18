package mx.utch.cazaobjetos.dto;
import java.util.UUID;
import java.time.Instant;
public record PhotoResponseDto(UUID id, String contentType, int sizeBytes, Instant createdAt, boolean referenceImage) {}
