package mx.utch.cazaobjetos.model;

import java.util.UUID;
import java.time.Instant;
import jakarta.persistence.*;
import mx.utch.cazaobjetos.enums.ClaimActivityKind;

@Entity
@Table(name="claim_evidence")
public class ClaimEvidenceModel {
    @Id
    @Column(name="id") private UUID id;
    @Column(name="claim_id") private UUID claimId;
    @Column(name="author_id") private UUID authorId;
    @Column(name="content_type") private String contentType;
    @Column(name="content") private byte[] content;
    @Column(name="size_bytes") private int sizeBytes;
    @Column(name="created_at") private Instant createdAt;
    @Column(name="reference_image") private boolean referenceImage;
    public boolean isReferenceImage() { return referenceImage; }
    public void setReferenceImage(boolean value) { referenceImage=value; }
    public UUID getId() { return id; }
    public void setId(UUID value) { id=value; }
    public UUID getClaimId() { return claimId; }
    public void setClaimId(UUID value) { claimId=value; }
    public UUID getAuthorId() { return authorId; }
    public void setAuthorId(UUID value) { authorId=value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { contentType=value; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] value) { content=value; }
    public int getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(int value) { sizeBytes=value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt=value; }
}
