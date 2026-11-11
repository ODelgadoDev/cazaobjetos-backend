package mx.utch.cazaobjetos.dto;

import java.time.*;
import java.util.UUID;
import mx.utch.cazaobjetos.enums.*;

public record UserResponseDto(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        Instant createdAt) {}
