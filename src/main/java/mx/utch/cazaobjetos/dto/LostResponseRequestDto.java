package mx.utch.cazaobjetos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LostResponseRequestDto(@NotBlank @Size(max=200) String location,
    @NotBlank @Size(max=2000) String body, @Size(max=2048) String photoUrl) {}
