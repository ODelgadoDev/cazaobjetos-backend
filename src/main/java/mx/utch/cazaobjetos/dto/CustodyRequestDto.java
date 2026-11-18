package mx.utch.cazaobjetos.dto;
import jakarta.validation.constraints.*;
import mx.utch.cazaobjetos.enums.CustodyStatus;
public record CustodyRequestDto(@NotNull CustodyStatus custody, @Size(max=120) String teacherName, @Size(max=200) String location) {}
