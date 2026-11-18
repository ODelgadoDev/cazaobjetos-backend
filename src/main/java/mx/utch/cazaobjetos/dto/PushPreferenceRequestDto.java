package mx.utch.cazaobjetos.dto;
import jakarta.validation.constraints.*;
public record PushPreferenceRequestDto(@NotNull @Min(0) @Max(2) Integer dailyReminders) {}
