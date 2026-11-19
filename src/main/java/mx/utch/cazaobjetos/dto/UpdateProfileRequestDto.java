package mx.utch.cazaobjetos.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;

public record UpdateProfileRequestDto(
        @NotBlank @Size(min = 3, max = 120) String displayName,
        @Size(max = 60) String alias,
        @Size(max = 80) String classroom,
        @Size(max = 80) String groupName,
        @Size(max = 120) String course,
        @NotBlank @Size(max = 160) String degree,
        @NotBlank @Size(max = 80) String building) {
    @JsonAnySetter
    public void unsupported(String field, Object value) {
        throw new IllegalArgumentException("Campo no permitido en el perfil");
    }
}
