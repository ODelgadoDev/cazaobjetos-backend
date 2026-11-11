package mx.utch.cazaobjetos.model;

import java.time.*;
import java.util.UUID;
import jakarta.persistence.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.dto.*;

@Entity
@Table(name = "email_outbox")
public class EmailOutboxModel {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "event_key")
    private String eventKey;

    @Column(name = "recipient")
    private String recipient;

    @Column(name = "subject")
    private String subject;

    @Column(name = "body")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private EmailStatus status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "attempts") private int attempts;
    @Column(name = "next_attempt_at") private Instant nextAttemptAt = Instant.now();
    public int getAttempts() { return attempts; }
    public void setAttempts(int value) { attempts = value; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant value) { nextAttemptAt = value; }

    public EmailOutboxModel() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public EmailStatus getStatus() { return status; }
    public void setStatus(EmailStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

}
