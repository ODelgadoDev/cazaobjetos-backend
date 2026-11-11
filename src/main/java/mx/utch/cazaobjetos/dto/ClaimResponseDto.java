package mx.utch.cazaobjetos.dto;

import java.time.Instant;
import java.util.UUID;
import mx.utch.cazaobjetos.enums.ClaimStatus;

public record ClaimResponseDto(UUID id, UUID foundItemId, UUID claimantId, UUID lostReportId,
        String privateDescription, ClaimStatus status, UUID acceptedBy, Instant acceptedAt,
        Instant completedAt, Instant createdAt, String responsibleEmail, String claimantEmail) {}
