package mx.utch.cazaobjetos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mx.utch.cazaobjetos.enums.CustodyStatus;

public record LostResponseRequestDto(@NotBlank @Size(max=200) String location,
    @NotBlank @Size(max=2000) String body, @Size(max=2048) String photoUrl,
    CustodyStatus custody, @Size(max=200) String custodyLocation) {
    public LostResponseRequestDto(String location, String body, String photoUrl) {
        this(location,body,photoUrl,null,null);
    }
}
