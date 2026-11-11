package mx.utch.cazaobjetos.dto;

import jakarta.validation.constraints.*;

public record EmailRequestDto(@NotBlank @Email @Size(max = 254) String email) {}
