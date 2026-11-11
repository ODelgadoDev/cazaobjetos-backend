package mx.utch.cazaobjetos.dto;

import java.util.UUID;
import jakarta.validation.constraints.*;

public record ClaimRequestDto(@NotBlank @Size(min = 10, max = 2000) String privateDescription, UUID lostReportId) {}
