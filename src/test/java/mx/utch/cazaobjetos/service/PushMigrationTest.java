package mx.utch.cazaobjetos.service;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PushMigrationTest {
    @Test void upgradeDoesNotSubscribeUsersOrReplayPendingNotifications()throws Exception {
        String url=System.getenv("TEST_DB_URL"),user=System.getenv().getOrDefault("TEST_DB_USER","caza_test"),password=System.getenv().getOrDefault("TEST_DB_PASSWORD","");String schema="push_upgrade_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()){
            Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target("12").load().migrate();connection.setSchema(schema);UUID actor=UUID.randomUUID(),found=UUID.randomUUID();
            sql.execute("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES('"+actor+"','push-migration@utch.edu.mx','hash','Persona','STUDENT',true)");
            sql.execute("INSERT INTO found_item(id,reporter_id,responsible_id,title,description,category,found_location,found_on,status,custody) VALUES('"+found+"','"+actor+"','"+actor+"','Mochila','Ficha','OTHER','I',current_date,'AVAILABLE','WITH_FINDER')");
            sql.execute("INSERT INTO user_notification(id,recipient_id,event_key,kind,target_type,target_id,title,body,created_at) VALUES(gen_random_uuid(),'"+actor+"','old-event','FOUND_PUBLISHED','FOUND_ITEM','"+found+"','Aviso','Resumen',now()-interval '1 day')");
            String before;String query="SELECT jsonb_build_object('users',(SELECT jsonb_agg(to_jsonb(u) ORDER BY id) FROM app_user u),'found',(SELECT jsonb_agg(to_jsonb(f) ORDER BY id) FROM found_item f),'notices',(SELECT jsonb_agg(to_jsonb(n) ORDER BY id) FROM user_notification n))::text";
            try(var rs=sql.executeQuery(query)){rs.next();before=rs.getString(1);}
            var latest=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").schemas(schema).defaultSchema(schema).load();assertEquals(1,latest.migrate().migrationsExecuted);
            try(var rs=sql.executeQuery(query)){rs.next();assertEquals(before,rs.getString(1));}
            for(String table:List.of("push_subscription","push_delivery","push_reminder_run","push_preference"))try(var rs=sql.executeQuery("SELECT count(*) FROM "+table)){rs.next();assertEquals(0,rs.getLong(1));}
            assertEquals(0,latest.migrate().migrationsExecuted);
        }finally{try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}}
    }
}
