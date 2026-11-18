package mx.utch.cazaobjetos.repository;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import mx.utch.cazaobjetos.model.NotificationModel;

public interface NotificationRepository extends JpaRepository<NotificationModel,UUID> {
    // Re-evaluate claim access for lists, counters and writes, including legacy delegation.
    String VISIBLE=" n.recipient_id=:actor AND n.cleared_at IS NULL AND (n.target_type<>'CLAIM' OR EXISTS (SELECT 1 FROM claim c JOIN found_item f ON f.id=c.found_item_id WHERE c.id=n.target_id AND (c.claimant_id=:actor OR f.responsible_id=:actor))) ";
    @Query(value="SELECT n.* FROM user_notification n WHERE "+VISIBLE+" AND (:unreadOnly=false OR n.read_at IS NULL) ORDER BY n.created_at DESC,n.id DESC",countQuery="SELECT count(*) FROM user_notification n WHERE "+VISIBLE+" AND (:unreadOnly=false OR n.read_at IS NULL)",nativeQuery=true)
    Page<NotificationModel> visible(@Param("actor") UUID actor,@Param("unreadOnly") boolean unreadOnly,Pageable page);
    @Query(value="SELECT n.* FROM user_notification n WHERE "+VISIBLE+" AND n.id=:id",nativeQuery=true)
    Optional<NotificationModel> visibleById(@Param("actor") UUID actor,@Param("id") UUID id);
    @Query(value="SELECT count(*) FROM user_notification n WHERE "+VISIBLE+" AND (:unreadOnly=false OR n.read_at IS NULL)",nativeQuery=true)
    long visibleCount(@Param("actor") UUID actor,@Param("unreadOnly") boolean unreadOnly);
    @Modifying
    @Query(value="UPDATE user_notification n SET read_at=coalesce(read_at,:now) WHERE "+VISIBLE+" AND n.id=:id",nativeQuery=true)
    int markRead(@Param("actor") UUID actor,@Param("id") UUID id,@Param("now") Instant now);
    @Modifying
    @Query(value="UPDATE user_notification n SET cleared_at=:now WHERE "+VISIBLE+" AND n.read_at IS NOT NULL",nativeQuery=true)
    int clearRead(@Param("actor") UUID actor,@Param("now") Instant now);
    @Modifying
    @Query(value="INSERT INTO user_notification(id,recipient_id,event_key,activity_id,kind,target_type,target_id,title,body,created_at) VALUES(:id,:recipient,:eventKey,:activity,:kind,:targetType,:target,:title,:body,:now) ON CONFLICT(recipient_id,event_key) DO NOTHING",nativeQuery=true)
    int insert(@Param("id") UUID id,@Param("recipient") UUID recipient,@Param("eventKey") String eventKey,@Param("activity") UUID activity,@Param("kind") String kind,@Param("targetType") String targetType,@Param("target") UUID target,@Param("title") String title,@Param("body") String body,@Param("now") Instant now);
}
