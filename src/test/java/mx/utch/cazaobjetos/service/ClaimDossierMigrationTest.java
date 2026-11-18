package mx.utch.cazaobjetos.service;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClaimDossierMigrationTest {
    @Test void upgradePreservesExistingDelegatedClosedClaimsAndImportsOnlyKnownHistory() throws Exception {
        String url=System.getenv("TEST_DB_URL"),user=System.getenv().getOrDefault("TEST_DB_USER","caza_test"),password=System.getenv().getOrDefault("TEST_DB_PASSWORD","");
        String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");
        var flyway=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target("9").load();
        try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()) {
            flyway.migrate();connection.setSchema(schema);
            UUID finder=UUID.randomUUID(),owner=UUID.randomUUID(),other=UUID.randomUUID(),secretary=UUID.randomUUID(),found=UUID.randomUUID(),claim=UUID.randomUUID(),closed=UUID.randomUUID();
            for(var entry:Map.of(finder,"finder",owner,"owner",other,"other",secretary,"secretary").entrySet()) {
                sql.execute("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES ('"+entry.getKey()+"','"+entry.getValue()+"@utch.edu.mx','unchanged-hash','"+entry.getValue()+"','"+(entry.getKey().equals(secretary)?"SECRETARY":"STUDENT")+"',true)");
            }
            sql.execute("INSERT INTO found_item(id,reporter_id,responsible_id,title,description,category,found_location,found_on,status,custody,office_marked_by,office_marked_at) VALUES ('"+found+"','"+finder+"','"+secretary+"','Objeto previo','Ficha anterior','OTHER','I',current_date,'RETURNED','OFFICE','"+finder+"',now()-interval '10 days')");
            sql.execute("INSERT INTO claim(id,found_item_id,claimant_id,private_description,status,accepted_by,accepted_at,completed_at,created_at) VALUES ('"+claim+"','"+found+"','"+owner+"','Prueba privada anterior','COMPLETED','"+secretary+"',now()-interval '9 days',now()-interval '8 days',now()-interval '10 days')");
            sql.execute("INSERT INTO claim(id,found_item_id,claimant_id,private_description,status,created_at) VALUES ('"+closed+"','"+found+"','"+other+"','Otra descripción privada','CLOSED',now()-interval '10 days')");
            sql.execute("INSERT INTO audit_event(id,actor_id,entity_id,action,created_at) VALUES (gen_random_uuid(),'"+secretary+"','"+claim+"','CLAIM_ACCEPT',now()-interval '9 days'),(gen_random_uuid(),'"+owner+"','"+claim+"','CLAIM_COMPLETE',now()-interval '8 days')");
            String before;
            try(var rs=sql.executeQuery("SELECT jsonb_agg(to_jsonb(c) ORDER BY id)::text FROM claim c")) {rs.next();before=rs.getString(1);}
            Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target("10").load().migrate();
            try(var rs=sql.executeQuery("SELECT jsonb_agg(to_jsonb(c) ORDER BY id)::text FROM claim c")) {rs.next();assertEquals(before,rs.getString(1));}
            try(var rs=sql.executeQuery("SELECT responsible_id,custody,teacher_name,custody_marked_by FROM found_item WHERE id='"+found+"'")) {
                rs.next();assertEquals(secretary,rs.getObject(1,UUID.class));assertEquals("OFFICE",rs.getString(2));assertNull(rs.getString(3));assertEquals(finder,rs.getObject(4,UUID.class));
            }
            try(var rs=sql.executeQuery("SELECT count(*) FROM claim_activity WHERE claim_id='"+claim+"'")) {rs.next();assertEquals(4,rs.getLong(1));}
            try(var rs=sql.executeQuery("SELECT actor_id,actor_name FROM claim_activity WHERE claim_id='"+closed+"' AND kind='CLOSED'")) {assertTrue(rs.next());assertNull(rs.getObject(1));assertEquals("Sistema",rs.getString(2));}
            try(var rs=sql.executeQuery("SELECT details_json,body FROM claim_activity WHERE claim_id='"+claim+"' AND kind='HISTORY_IMPORTED'")) {rs.next();assertTrue(rs.getString(1).contains("imported"));assertTrue(rs.getString(2).contains("no al crear"));}
            assertEquals(0,Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").schemas(schema).defaultSchema(schema).target("10").load().migrate().migrationsExecuted);
        } finally {
            try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()) {sql.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");}
        }
    }
}
