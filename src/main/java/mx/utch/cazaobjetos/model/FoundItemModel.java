package mx.utch.cazaobjetos.model;

import java.time.*;
import java.util.UUID;
import jakarta.persistence.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.dto.*;

@Entity
@Table(name = "found_item")
public class FoundItemModel {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "reporter_id")
    private UUID reporterId;

    @Column(name = "responsible_id")
    private UUID responsibleId;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private Category category;

    @Column(name = "found_location")
    private String foundLocation;

    @Column(name = "found_on")
    private LocalDate foundOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private FoundStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "custody")
    private CustodyStatus custody;

    @Column(name = "office_marked_by")
    private UUID officeMarkedBy;

    @Column(name = "office_marked_at")
    private Instant officeMarkedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    public FoundItemModel() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getReporterId() { return reporterId; }
    public void setReporterId(UUID reporterId) { this.reporterId = reporterId; }

    public UUID getResponsibleId() { return responsibleId; }
    public void setResponsibleId(UUID responsibleId) { this.responsibleId = responsibleId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public String getFoundLocation() { return foundLocation; }
    public void setFoundLocation(String foundLocation) { this.foundLocation = foundLocation; }

    public LocalDate getFoundOn() { return foundOn; }
    public void setFoundOn(LocalDate foundOn) { this.foundOn = foundOn; }

    public FoundStatus getStatus() { return status; }
    public void setStatus(FoundStatus status) { this.status = status; }

    public CustodyStatus getCustody() { return custody; }
    public void setCustody(CustodyStatus custody) { this.custody = custody; }

    public UUID getOfficeMarkedBy() { return officeMarkedBy; }
    public void setOfficeMarkedBy(UUID officeMarkedBy) { this.officeMarkedBy = officeMarkedBy; }

    public Instant getOfficeMarkedAt() { return officeMarkedAt; }
    public void setOfficeMarkedAt(Instant officeMarkedAt) { this.officeMarkedAt = officeMarkedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public FoundItemResponseDto toResponseDto() {
        return new FoundItemResponseDto(id, reporterId, responsibleId, title, description, category, foundLocation, foundOn, status, custody, officeMarkedBy, officeMarkedAt, createdAt);
    }
}
