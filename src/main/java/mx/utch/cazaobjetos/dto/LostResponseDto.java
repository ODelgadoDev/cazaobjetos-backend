package mx.utch.cazaobjetos.dto;

import java.time.Instant;
import java.util.UUID;

public record LostResponseDto(UUID id, UUID lostReportId, UUID finderId, String finderName, UUID ownerId,
    String ownerName, String location, String body, String status, Instant createdAt, String photoType,
    String custody, String custodyLocation, UUID finderAvatarVersion, String finderDegree) {}
