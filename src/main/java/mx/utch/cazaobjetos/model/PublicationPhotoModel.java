package mx.utch.cazaobjetos.model;
import java.util.UUID;
import java.time.Instant;
import jakarta.persistence.*;
@Entity
@Table(name="publication_photo")
public class PublicationPhotoModel {
    @Id
    @Column(name="id") private UUID id;
    @Column(name="found_item_id") private UUID foundItemId;
    @Column(name="lost_report_id") private UUID lostReportId;
    @Column(name="content_type") private String contentType;
    @Column(name="content") private byte[] content;
    @Column(name="size_bytes") private int sizeBytes;
    @Column(name="created_at") private Instant createdAt;
    public UUID getId() { return id; }
    public void setId(UUID value) { id=value; }
    public UUID getFoundItemId() { return foundItemId; }
    public void setFoundItemId(UUID value) { foundItemId=value; }
    public UUID getLostReportId() { return lostReportId; }
    public void setLostReportId(UUID value) { lostReportId=value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { contentType=value; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] value) { content=value; }
    public int getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(int value) { sizeBytes=value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt=value; }
}
