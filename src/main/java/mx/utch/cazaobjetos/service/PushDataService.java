package mx.utch.cazaobjetos.service;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
@Service
public class PushDataService {
    public record Subscription(UUID id,UUID owner,String endpoint,String p256dh,String auth,String binding,String session,long credentials,boolean active) {}
    public record Delivery(UUID id,UUID subscription,UUID owner,String binding,UUID notification,String kind,String eventKey,int attempts,Instant expires) {}
    private final JdbcTemplate db;
    public PushDataService(JdbcTemplate db){this.db=db;}
    private Subscription subscription(ResultSet r,int row)throws SQLException{return new Subscription(r.getObject("id",UUID.class),r.getObject("recipient_id",UUID.class),r.getString("endpoint"),r.getString("p256dh"),r.getString("auth_secret"),r.getString("binding_token"),r.getString("session_hash"),r.getLong("credentials_version"),r.getBoolean("active"));}
    public void lockEndpoint(String hash){db.queryForObject("SELECT pg_advisory_xact_lock(?)",Object.class,java.nio.ByteBuffer.wrap(HexFormat.of().parseHex(hash)).getLong());}
    public long lockUser(UUID id){return db.queryForObject("SELECT credentials_version FROM app_user WHERE id=? AND enabled FOR UPDATE",Long.class,id);}
    public void expireCredentials(UUID user,long version){db.update("UPDATE push_subscription SET active=false WHERE recipient_id=? AND credentials_version<>?",user,version);}
    public long activeCount(UUID user){return db.queryForObject("SELECT count(*) FROM push_subscription WHERE recipient_id=? AND active",Long.class,user);}
    public Optional<Subscription> byHash(String hash){return db.query("SELECT * FROM push_subscription WHERE endpoint_hash=? FOR UPDATE",this::subscription,hash).stream().findFirst();}
    public Optional<Subscription> lock(UUID id){return db.query("SELECT * FROM push_subscription WHERE id=? FOR UPDATE",this::subscription,id).stream().findFirst();}
    public boolean credentialsValid(Subscription s){return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM app_user WHERE id=? AND enabled AND credentials_version=?)",Boolean.class,s.owner(),s.credentials()));}
    public void insert(Subscription s,String hash,Instant now){db.update("INSERT INTO push_subscription(id,recipient_id,endpoint,endpoint_hash,p256dh,auth_secret,binding_token,session_hash,credentials_version,active,created_at,last_seen_at) VALUES(?,?,?,?,?,?,?,?,?,true,?,?)",s.id(),s.owner(),s.endpoint(),hash,s.p256dh(),s.auth(),s.binding(),s.session(),s.credentials(),Timestamp.from(now),Timestamp.from(now));}
    public void update(Subscription s,Instant now){db.update("UPDATE push_subscription SET recipient_id=?,p256dh=?,auth_secret=?,binding_token=?,session_hash=?,credentials_version=?,active=true,last_seen_at=? WHERE id=?",s.owner(),s.p256dh(),s.auth(),s.binding(),s.session(),s.credentials(),Timestamp.from(now),s.id());}
    public void disable(UUID id){db.update("UPDATE push_subscription SET active=false WHERE id=?",id);}
    public void loginSession(String session){db.update("UPDATE push_subscription SET active=false WHERE session_hash=?",session);}
    public void logout(UUID user,String session){db.update("UPDATE push_subscription SET active=false WHERE recipient_id=? AND session_hash=?",user,session);}
    public int preference(UUID user){return db.query("SELECT daily_reminders FROM push_preference WHERE recipient_id=?",(r,n)->r.getInt(1),user).stream().findFirst().orElse(1);}
    public void preference(UUID user,int frequency){db.update("INSERT INTO push_preference(recipient_id,daily_reminders) VALUES(?,?) ON CONFLICT(recipient_id) DO UPDATE SET daily_reminders=excluded.daily_reminders",user,frequency);}
    public void queueNotice(UUID user,UUID notice,Instant now){db.update("INSERT INTO push_delivery(id,subscription_id,recipient_id,binding_token,notification_id,kind,event_key,created_at,expires_at,next_attempt_at) SELECT gen_random_uuid(),s.id,s.recipient_id,s.binding_token,?,'EVENT',?,?,?,? FROM push_subscription s JOIN app_user u ON u.id=s.recipient_id WHERE s.recipient_id=? AND s.active AND u.enabled AND s.credentials_version=u.credentials_version ON CONFLICT(subscription_id,binding_token,event_key) DO NOTHING",notice,"notice:"+notice,Timestamp.from(now),Timestamp.from(now.plusSeconds(3600)),Timestamp.from(now),user);}
    public UUID queue(Subscription s,String kind,String key,Instant now){UUID id=UUID.randomUUID();int count=db.update("INSERT INTO push_delivery(id,subscription_id,recipient_id,binding_token,kind,event_key,created_at,expires_at,next_attempt_at) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(subscription_id,binding_token,event_key) DO NOTHING",id,s.id(),s.owner(),s.binding(),kind,key,Timestamp.from(now),Timestamp.from(now.plusSeconds(kind.equals("TEST")?300:3600)),Timestamp.from(now));return count==1?id:null;}
    public boolean recentTest(UUID sub,Instant since){return db.queryForObject("SELECT EXISTS(SELECT 1 FROM push_delivery WHERE subscription_id=? AND kind='TEST' AND created_at>?)",Boolean.class,sub,Timestamp.from(since));}
    public List<Delivery> due(Instant now){return db.query("SELECT * FROM push_delivery WHERE status='PENDING' AND next_attempt_at<=? ORDER BY created_at,id LIMIT 5 FOR UPDATE SKIP LOCKED",(r,n)->new Delivery(r.getObject("id",UUID.class),r.getObject("subscription_id",UUID.class),r.getObject("recipient_id",UUID.class),r.getString("binding_token"),r.getObject("notification_id",UUID.class),r.getString("kind"),r.getString("event_key"),r.getInt("attempts"),r.getTimestamp("expires_at").toInstant()),Timestamp.from(now));}
    public void finish(UUID id,String state,int attempts,Integer status,Instant now,Instant next){db.update("UPDATE push_delivery SET status=?,attempts=?,last_status=?,sent_at=?,next_attempt_at=? WHERE id=?",state,attempts,status,state.equals("SENT")?Timestamp.from(now):null,Timestamp.from(next),id);}
    public List<UUID> reminderUsers(){return db.query("SELECT DISTINCT s.recipient_id FROM push_subscription s JOIN app_user u ON u.id=s.recipient_id LEFT JOIN push_preference p ON p.recipient_id=s.recipient_id WHERE s.active AND u.enabled AND s.credentials_version=u.credentials_version AND coalesce(p.daily_reminders,1)>0",(r,n)->r.getObject(1,UUID.class));}
    public List<Subscription> active(UUID user){return db.query("SELECT s.* FROM push_subscription s JOIN app_user u ON u.id=s.recipient_id WHERE s.recipient_id=? AND s.active AND u.enabled AND s.credentials_version=u.credentials_version",this::subscription,user);}
    public boolean activeWork(UUID user){return db.queryForObject("SELECT EXISTS(SELECT 1 FROM claim c JOIN found_item f ON f.id=c.found_item_id WHERE c.status IN ('PENDING','ACCEPTED') AND f.status='AVAILABLE' AND (c.claimant_id=? OR f.responsible_id=?)) OR EXISTS(SELECT 1 FROM lost_report WHERE owner_id=? AND status='ACTIVE')",Boolean.class,user,user,user);}
    public boolean reminderRun(UUID user,LocalDate date,int slot,Instant now){return db.update("INSERT INTO push_reminder_run(recipient_id,local_date,slot,created_at) VALUES(?,?,?,?) ON CONFLICT DO NOTHING",user,java.sql.Date.valueOf(date),slot,Timestamp.from(now))==1;}
}
