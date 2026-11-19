package mx.utch.cazaobjetos.dto;

import java.time.*;
import java.util.UUID;
import mx.utch.cazaobjetos.enums.*;

public record FoundItemResponseDto(
        UUID id,
        UUID reporterId,
        UUID responsibleId,
        String title,
        String description,
        Category category,
        String foundLocation,
        LocalDate foundOn,
        FoundStatus status,
        CustodyStatus custody,
        UUID officeMarkedBy,
        Instant officeMarkedAt,
        Instant createdAt,
        String teacherName,
        String custodyLocation,
        UUID custodyMarkedBy,
        Instant custodyMarkedAt,
        UUID sourceLostReportId, String reporterName, String responsibleName) {}
