package mx.utch.cazaobjetos.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.*;
import mx.utch.cazaobjetos.enums.Category;

public record ReportRequestDto(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 2000) String description,
        @NotNull Category category,
        @NotBlank @Size(max = 200) String location,
        @NotNull @PastOrPresent LocalDate occurredOn) {}
