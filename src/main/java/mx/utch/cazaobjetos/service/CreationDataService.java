package mx.utch.cazaobjetos.service;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
public class CreationDataService {
    private final JdbcTemplate db;
    public CreationDataService(JdbcTemplate db) { this.db=db; }
    public record Saved(String hash,UUID id) {}
    public void lock(long key) { db.query("select pg_advisory_xact_lock(?)",rs->{},key); }
    public Optional<Saved> find(UUID actor,String scope,String key) {
        return db.query("select request_hash,resource_id from creation_request where actor_id=? and scope=? and request_key=?",
            (rs,n)->new Saved(rs.getString(1),rs.getObject(2,UUID.class)),actor,scope,key).stream().findFirst();
    }
    public void save(UUID actor,String scope,String key,String hash,UUID id) {
        db.update("insert into creation_request(actor_id,scope,request_key,request_hash,resource_id) values(?,?,?,?,?)",actor,scope,key,hash,id);
    }
}
