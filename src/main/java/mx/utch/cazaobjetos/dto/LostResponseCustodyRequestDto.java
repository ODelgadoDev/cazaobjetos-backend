package mx.utch.cazaobjetos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mx.utch.cazaobjetos.enums.CustodyStatus;

public record LostResponseCustodyRequestDto(@NotNull CustodyStatus custody, @Size(max=200) String location) {}
