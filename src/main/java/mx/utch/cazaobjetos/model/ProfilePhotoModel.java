package mx.utch.cazaobjetos.model;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "user_profile_photo")
public class ProfilePhotoModel {
    @Id @Column(name = "user_id") private UUID userId;
    @Column(name = "version") private UUID version;
    @Column(name = "content_type") private String contentType;
    @Column(name = "content") private byte[] content;
    @Column(name = "size_bytes") private int sizeBytes;
    @Column(name = "updated_at") private Instant updatedAt;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID value) { userId = value; }
    public UUID getVersion() { return version; }
    public void setVersion(UUID value) { version = value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { contentType = value; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] value) { content = value; }
    public int getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(int value) { sizeBytes = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}
