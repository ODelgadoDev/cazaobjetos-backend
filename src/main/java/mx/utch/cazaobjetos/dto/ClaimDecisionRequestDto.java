package mx.utch.cazaobjetos.dto;
import jakarta.validation.constraints.Size;
public record ClaimDecisionRequestDto(@Size(max=2000) String reason) {}
