package mx.utch.cazaobjetos.service;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NotificationMigrationTest {
    @Test void existingActiveCasesReceiveOneIntroductionWithoutChangingTheirHistory() throws Exception {
        String url=System.getenv("TEST_DB_URL"),user=System.getenv().getOrDefault("TEST_DB_USER","caza_test"),password=System.getenv().getOrDefault("TEST_DB_PASSWORD","");
        String schema="notify_upgrade_"+UUID.randomUUID().toString().replace("-","");
        try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()) {
            Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target("10").load().migrate();connection.setSchema(schema);
            UUID finder=UUID.randomUUID(),owner=UUID.randomUUID(),other=UUID.randomUUID(),found=UUID.randomUUID(),claim=UUID.randomUUID(),rejected=UUID.randomUUID();
            for(var id:List.of(finder,owner,other))sql.execute("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES('"+id+"','"+id+"@utch.edu.mx','hash-unchanged','Persona','STUDENT',true)");
            sql.execute("INSERT INTO found_item(id,reporter_id,responsible_id,title,description,category,found_location,found_on,status,custody) VALUES('"+found+"','"+finder+"','"+finder+"','Objeto anterior','Ficha anterior','OTHER','I',current_date,'AVAILABLE','WITH_FINDER')");
            sql.execute("INSERT INTO claim(id,found_item_id,claimant_id,private_description,status,created_at) VALUES('"+claim+"','"+found+"','"+owner+"','SECRETO','PENDING',now()-interval '10 days'),('"+rejected+"','"+found+"','"+other+"','OTRO SECRETO','REJECTED',now()-interval '10 days')");
            String before;
            try(var rs=sql.executeQuery("SELECT jsonb_agg(to_jsonb(c) ORDER BY id)::text FROM claim c")){rs.next();before=rs.getString(1);}
            var latest=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target("11").load();assertEquals(1,latest.migrate().migrationsExecuted);
            try(var rs=sql.executeQuery("SELECT jsonb_agg(to_jsonb(c) ORDER BY id)::text FROM claim c")){rs.next();assertEquals(before,rs.getString(1));}
            try(var rs=sql.executeQuery("SELECT recipient_id,kind,target_id,read_at,cleared_at,body FROM user_notification")) {
                var recipients=new HashSet<UUID>();int count=0;
                while(rs.next()){count++;recipients.add(rs.getObject(1,UUID.class));assertEquals("IN_PROGRESS",rs.getString(2));assertEquals(claim,rs.getObject(3,UUID.class));assertNull(rs.getObject(4));assertNull(rs.getObject(5));assertFalse(rs.getString(6).contains("SECRETO"));}
                assertEquals(2,count);assertEquals(Set.of(finder,owner),recipients);
            }
            try(var rs=sql.executeQuery("SELECT count(*) FROM claim_activity")){rs.next();assertEquals(0,rs.getLong(1));}
            assertEquals(0,latest.migrate().migrationsExecuted);
        } finally {try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()){sql.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}}
    }
}
