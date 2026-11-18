package mx.utch.cazaobjetos.dto;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
public record FoundPublicationRequestDto(@NotNull @Valid ReportRequestDto report, UUID sourceLostReportId, @NotNull @Valid CustodyRequestDto custody) {}
