package mx.utch.cazaobjetos.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.util.EvidenceImageUtil.Image;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class LostResponseRepository {
    private static final String SELECT = """
        SELECT r.id,r.lost_report_id,r.finder_id,f.display_name AS finder_name,l.owner_id,
               o.display_name AS owner_name,r.location,r.body,r.status,r.created_at,r.photo_type,r.custody,r.custody_location,p.version AS finder_avatar_version,f.degree AS finder_degree
        FROM lost_response r JOIN lost_report l ON l.id=r.lost_report_id
        JOIN app_user f ON f.id=r.finder_id JOIN app_user o ON o.id=l.owner_id
        LEFT JOIN user_profile_photo p ON p.user_id=r.finder_id
        WHERE r.lost_report_id=?
        """;
    private static final RowMapper<LostResponseDto> RESPONSE = (rs, row) -> new LostResponseDto(
        rs.getObject("id", UUID.class),rs.getObject("lost_report_id", UUID.class),rs.getObject("finder_id", UUID.class),
        rs.getString("finder_name"),rs.getObject("owner_id", UUID.class),rs.getString("owner_name"),rs.getString("location"),
        rs.getString("body"),rs.getString("status"),rs.getObject("created_at",OffsetDateTime.class).toInstant(),
        rs.getString("photo_type")==null?null:"UPLOAD",rs.getString("custody"),rs.getString("custody_location"),rs.getObject("finder_avatar_version",UUID.class),rs.getString("finder_degree"));
    private final JdbcTemplate db;

    public LostResponseRepository(JdbcTemplate db) { this.db=db; }

    public List<LostResponseDto> visible(UUID loss, UUID actor) {
        return db.query(SELECT+" AND (r.finder_id=? OR l.owner_id=?) ORDER BY r.created_at DESC,r.id DESC",
            RESPONSE,loss,actor,actor);
    }
    public Optional<LostResponseDto> find(UUID loss, UUID id) {
        return db.query(SELECT+" AND r.id=?",RESPONSE,loss,id).stream().findFirst();
    }
    public boolean exists(UUID loss, UUID finder, boolean openOnly) {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS (SELECT 1 FROM lost_response WHERE lost_report_id=? AND finder_id=?
                AND (NOT ? OR status='OPEN'))
            """,Boolean.class,loss,finder,openOnly));
    }
    public void insert(UUID id, UUID loss, UUID finder, String location, String body, Image photo, String custody, String custodyLocation) {
        db.update("""
            INSERT INTO lost_response(id,lost_report_id,finder_id,location,body,status,created_at,photo_type,photo,custody,custody_location)
            VALUES(?,?,?,?,?,'OPEN',now(),?,?,?,?)
            """,id,loss,finder,location,body,photo==null?null:photo.type(),photo==null?null:photo.bytes(),custody,custodyLocation);
    }
    public void changeCustody(UUID response, String custody, String location) {
        db.update("UPDATE lost_response SET custody=?,custody_location=? WHERE id=?",custody,location,response);
    }
    public void changeStatus(UUID response, String status) {
        db.update("UPDATE lost_response SET status=? WHERE id=?",status,response);
    }
    public void cancelOpen(UUID loss) {
        db.update("UPDATE lost_response SET status='CANCELLED' WHERE lost_report_id=? AND status='OPEN'",loss);
    }
    public record Photo(String contentType, byte[] content) {}
    public Optional<Photo> photo(UUID response) {
        return db.query("SELECT photo_type,photo FROM lost_response WHERE id=? AND photo IS NOT NULL",
            (rs,row)->new Photo(rs.getString(1),rs.getBytes(2)),response).stream().findFirst();
    }
    private static final String SELECT_COMMENT = """
        SELECT c.id,c.author_id,u.display_name,c.body,c.created_at,p.version AS author_avatar_version,u.degree AS author_degree
        FROM lost_response_comment c JOIN app_user u ON u.id=c.author_id
        LEFT JOIN user_profile_photo p ON p.user_id=c.author_id WHERE c.response_id=?
        """;
    private static final RowMapper<LostResponseCommentDto> COMMENT = (rs,row)->new LostResponseCommentDto(
        rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getObject(5,OffsetDateTime.class).toInstant(),rs.getObject(6,UUID.class),rs.getString(7));
    public List<LostResponseCommentDto> comments(UUID response) {
        return db.query(SELECT_COMMENT+" ORDER BY c.created_at,c.id",COMMENT,response);
    }
    public LostResponseCommentDto comment(UUID response, UUID id) {
        return db.queryForObject(SELECT_COMMENT+" AND c.id=?",COMMENT,response,id);
    }
    public void insertComment(UUID id, UUID response, UUID actor, String body) {
        db.update("INSERT INTO lost_response_comment(id,response_id,author_id,body,created_at) VALUES(?,?,?,?,now())",
            id,response,actor,body);
    }
}
