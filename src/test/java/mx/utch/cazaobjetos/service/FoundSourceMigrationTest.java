package mx.utch.cazaobjetos.service;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FoundSourceMigrationTest {
    @Test void upgradeDoesNotInferLinksRewriteNoticesOrReplayEmails() throws Exception {
        String url=System.getenv("TEST_DB_URL"),user=System.getenv().getOrDefault("TEST_DB_USER","caza_test"),password=System.getenv().getOrDefault("TEST_DB_PASSWORD","");String schema="source_upgrade_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()) {
            Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target("11").load().migrate();connection.setSchema(schema);
            UUID actor=UUID.randomUUID(),found=UUID.randomUUID(),loss=UUID.randomUUID(),notice=UUID.randomUUID();
            sql.execute("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES('"+actor+"','migration@utch.edu.mx','hash','Persona','STUDENT',true)");
            sql.execute("INSERT INTO lost_report(id,owner_id,title,description,category,lost_location,lost_on,status) VALUES('"+loss+"','"+actor+"','Mochila','Descripción','OTHER','I',current_date,'ACTIVE')");
            sql.execute("INSERT INTO found_item(id,reporter_id,responsible_id,title,description,category,found_location,found_on,status,custody) VALUES('"+found+"','"+actor+"','"+actor+"','Mochila','Descripción','OTHER','I',current_date,'AVAILABLE','WITH_FINDER')");
            sql.execute("INSERT INTO user_notification(id,recipient_id,event_key,kind,target_type,target_id,title,body,created_at,read_at,cleared_at) VALUES('"+notice+"','"+actor+"','old-event','FOUND_PUBLISHED','FOUND_ITEM','"+found+"','Título','Resumen',now()-interval '2 days',now()-interval '1 day',now())");
            String before,notices;
            try(var rs=sql.executeQuery("SELECT to_jsonb(f)::text FROM found_item f")){rs.next();before=rs.getString(1);}
            try(var rs=sql.executeQuery("SELECT to_jsonb(n)::text FROM user_notification n")){rs.next();notices=rs.getString(1);}
            var latest=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target("12").load();assertEquals(1,latest.migrate().migrationsExecuted);
            try(var rs=sql.executeQuery("SELECT (to_jsonb(f)-'source_lost_report_id')::text,source_lost_report_id FROM found_item f")){rs.next();assertEquals(before,rs.getString(1));assertNull(rs.getObject(2));}
            try(var rs=sql.executeQuery("SELECT to_jsonb(n)::text FROM user_notification n")){rs.next();assertEquals(notices,rs.getString(1));}
            try(var rs=sql.executeQuery("SELECT count(*) FROM email_outbox")){rs.next();assertEquals(0,rs.getLong(1));}
            assertThrows(SQLException.class,()->sql.execute("UPDATE found_item SET source_lost_report_id='"+UUID.randomUUID()+"'"));assertEquals(0,latest.migrate().migrationsExecuted);
        }finally{try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}}
    }
}
