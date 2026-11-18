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

    @Column(name = "alias", length = 60)
    private String alias;

    @Column(name = "classroom", length = 80)
    private String classroom;

    @Column(name = "group_name", length = 80)
    private String groupName;

    @Column(name = "course", length = 120)
    private String course;

    @Column(name = "degree", length = 160)
    private String degree;

    @Column(name = "building", length = 80)
    private String building;

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

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public String getClassroom() { return classroom; }
    public void setClassroom(String classroom) { this.classroom = classroom; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getCourse() { return course; }
    public void setCourse(String course) { this.course = course; }

    public String getDegree() { return degree; }
    public void setDegree(String degree) { this.degree = degree; }

    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public boolean getEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public UserResponseDto toResponseDto() { return toResponseDto(null); }

    public UserResponseDto toResponseDto(UUID avatarVersion) {
        return new UserResponseDto(id, email, displayName, role, createdAt, alias, classroom, groupName, course, degree, building,
                mx.utch.cazaobjetos.util.StudentNumberUtil.fromEmail(email, role), avatarVersion);
    }
}
