package mx.utch.cazaobjetos.dto;

import java.time.*;
import java.util.UUID;
import mx.utch.cazaobjetos.enums.*;

public record LostReportResponseDto(
        UUID id,
        UUID ownerId,
        String title,
        String description,
        Category category,
        String lostLocation,
        LocalDate lostOn,
        LostStatus status,
        Instant recoveredAt,
        Instant createdAt) {}
