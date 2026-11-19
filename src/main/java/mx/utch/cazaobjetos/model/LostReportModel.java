package mx.utch.cazaobjetos.model;

import java.time.*;
import java.util.UUID;
import jakarta.persistence.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.dto.*;

@Entity
@Table(name = "lost_report")
public class LostReportModel {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private Category category;

    @Column(name = "lost_location")
    private String lostLocation;

    @Column(name = "lost_on")
    private LocalDate lostOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private LostStatus status;

    @Column(name = "recovered_at")
    private Instant recoveredAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @org.hibernate.annotations.Formula("(select u.display_name from app_user u where u.id = owner_id)")
    private String ownerName;
    @org.hibernate.annotations.Formula("(select u.degree from app_user u where u.id=owner_id)")
    private String ownerDegree;

    @org.hibernate.annotations.Formula("(exists (select 1 from lost_response r where r.lost_report_id = id and r.status = 'OPEN'))")
    private boolean hasResponse;

    public LostReportModel() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public String getLostLocation() { return lostLocation; }
    public void setLostLocation(String lostLocation) { this.lostLocation = lostLocation; }

    public LocalDate getLostOn() { return lostOn; }
    public void setLostOn(LocalDate lostOn) { this.lostOn = lostOn; }

    public LostStatus getStatus() { return status; }
    public void setStatus(LostStatus status) { this.status = status; }

    public Instant getRecoveredAt() { return recoveredAt; }
    public void setRecoveredAt(Instant recoveredAt) { this.recoveredAt = recoveredAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public LostReportResponseDto toResponseDto() {
        return new LostReportResponseDto(id, ownerId, title, description, category, lostLocation, lostOn, status, recoveredAt, createdAt, ownerName, hasResponse, ownerDegree);
    }
}
