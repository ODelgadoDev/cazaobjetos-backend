package mx.utch.cazaobjetos.model;

import java.time.*;
import java.util.UUID;
import jakarta.persistence.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.dto.*;

@Entity
@Table(name = "app_user")
public class UserModel {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "email")
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private UserRole role;

    @Column(name = "enabled")
    private boolean enabled;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public void setEmailVerifiedAt(Instant value) { this.emailVerifiedAt = value; }

    @Column(name="credentials_version") private long credentialsVersion;
    public long getCredentialsVersion() { return credentialsVersion; }
    public void setCredentialsVersion(long value) { credentialsVersion=value; }
    public UserModel() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public boolean getEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public UserResponseDto toResponseDto() {
        return new UserResponseDto(id, email, displayName, role, createdAt);
    }
}
