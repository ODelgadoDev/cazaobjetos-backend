package mx.utch.cazaobjetos.model;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import mx.utch.cazaobjetos.dto.NotificationResponseDto;
import mx.utch.cazaobjetos.enums.*;
@Entity
@Table(name="user_notification")
public class NotificationModel {
    @Id private UUID id;
    @Column(name="recipient_id") private UUID recipientId;
    @Column(name="event_key") private String eventKey;
    @Column(name="activity_id") private UUID activityId;
    @Enumerated(EnumType.STRING) private NotificationKind kind;
    @Enumerated(EnumType.STRING) @Column(name="target_type") private NotificationTarget targetType;
    @Column(name="target_id") private UUID targetId;
    private String title;
    private String body;
    @Column(name="created_at") private Instant createdAt;
    @Column(name="read_at") private Instant readAt;
    @Column(name="cleared_at") private Instant clearedAt;
    @Column(name="actor_name") private String actorName;
    @Column(name="object_title") private String objectTitle;
    @Column(name="response_id") private UUID responseId;
    public NotificationResponseDto toResponseDto() { return new NotificationResponseDto(id,kind,targetType,targetId,title,body,createdAt,readAt,actorName,objectTitle,title,responseId); }
}
