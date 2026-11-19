package mx.utch.cazaobjetos.model;

import java.util.UUID;
import java.time.Instant;
import jakarta.persistence.*;
import mx.utch.cazaobjetos.enums.ClaimActivityKind;

@Entity
@Table(name="claim_activity")
public class ClaimActivityModel {
    @Id
    @Column(name="id") private UUID id;
    @Column(name="sequence", insertable=false, updatable=false) private long sequence;
    @Column(name="claim_id") private UUID claimId;
    @Column(name="actor_id") private UUID actorId;
    @Column(name="actor_name") private String actorName;
    @Enumerated(EnumType.STRING)
    @Column(name="kind") private ClaimActivityKind kind;
    @Column(name="body") private String body;
    @Column(name="details_json") private String detailsJson;
    @Column(name="evidence_id") private UUID evidenceId;
    @Column(name="request_key") private String requestKey;
    @Column(name="request_hash") private String requestHash;
    @Column(name="created_at") private Instant createdAt;
    @org.hibernate.annotations.Formula("(select p.version from user_profile_photo p where p.user_id=actor_id)")
    private UUID actorAvatarVersion;
    @org.hibernate.annotations.Formula("(select u.degree from app_user u where u.id=actor_id)")
    private String actorDegree;
    public String getActorDegree() { return actorDegree; }
    public UUID getActorAvatarVersion() { return actorAvatarVersion; }
    public UUID getId() { return id; }
    public void setId(UUID value) { id=value; }
    public long getSequence() { return sequence; }
    public UUID getClaimId() { return claimId; }
    public void setClaimId(UUID value) { claimId=value; }
    public UUID getActorId() { return actorId; }
    public void setActorId(UUID value) { actorId=value; }
    public String getActorName() { return actorName; }
    public void setActorName(String value) { actorName=value; }
    public ClaimActivityKind getKind() { return kind; }
    public void setKind(ClaimActivityKind value) { kind=value; }
    public String getBody() { return body; }
    public void setBody(String value) { body=value; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String value) { detailsJson=value; }
    public UUID getEvidenceId() { return evidenceId; }
    public void setEvidenceId(UUID value) { evidenceId=value; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String value) { requestKey=value; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String value) { requestHash=value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt=value; }
}
