package mx.utch.cazaobjetos.model;

import java.time.*;
import java.util.UUID;
import jakarta.persistence.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.dto.*;

@Entity
@Table(name = "claim")
public class ClaimModel {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "found_item_id")
    private UUID foundItemId;

    @Column(name = "claimant_id")
    private UUID claimantId;

    @Column(name = "lost_report_id")
    private UUID lostReportId;

    @Column(name = "private_description")
    private String privateDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ClaimStatus status;

    @Column(name = "accepted_by")
    private UUID acceptedBy;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    public ClaimModel() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getFoundItemId() { return foundItemId; }
    public void setFoundItemId(UUID foundItemId) { this.foundItemId = foundItemId; }

    public UUID getClaimantId() { return claimantId; }
    public void setClaimantId(UUID claimantId) { this.claimantId = claimantId; }

    public UUID getLostReportId() { return lostReportId; }
    public void setLostReportId(UUID lostReportId) { this.lostReportId = lostReportId; }

    public String getPrivateDescription() { return privateDescription; }
    public void setPrivateDescription(String privateDescription) { this.privateDescription = privateDescription; }

    public ClaimStatus getStatus() { return status; }
    public void setStatus(ClaimStatus status) { this.status = status; }

    public UUID getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(UUID acceptedBy) { this.acceptedBy = acceptedBy; }

    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
