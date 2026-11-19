package mx.utch.cazaobjetos.dto;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record FoundPublicationRequestDto(@NotNull @Valid ReportRequestDto report, UUID sourceLostReportId, @NotNull @Valid CustodyRequestDto custody, @Size(max=2048) String photoUrl, Boolean referenceImage) {
    public FoundPublicationRequestDto(ReportRequestDto report, UUID sourceLostReportId, CustodyRequestDto custody) {
        this(report,sourceLostReportId,custody,null,false);
    }
}
