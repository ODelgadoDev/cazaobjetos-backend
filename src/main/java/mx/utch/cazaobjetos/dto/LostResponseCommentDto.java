package mx.utch.cazaobjetos.dto;

import java.time.Instant;
import java.util.UUID;

public record LostResponseCommentDto(UUID id, UUID authorId, String authorName, String body, Instant createdAt) {}
