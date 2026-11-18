package mx.utch.cazaobjetos.dto;
import jakarta.validation.constraints.*;
public record ImportPhotoRequestDto(@NotBlank @Size(max=2048) String url, boolean referenceImage) {}
