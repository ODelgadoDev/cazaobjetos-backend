package mx.utch.cazaobjetos.model;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "email_verification")
public class EmailVerificationModel {
    @Id @Column(name = "user_id") private UUID userId;
    @Column(name = "token_hash") private String tokenHash;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "consumed_at") private Instant consumedAt;

    public EmailVerificationModel() {}
    public UUID getUserId() { return userId; }
    public void setUserId(UUID value) { userId = value; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String value) { tokenHash = value; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant value) { expiresAt = value; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant value) { sentAt = value; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant value) { consumedAt = value; }
}
