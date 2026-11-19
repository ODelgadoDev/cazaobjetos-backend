package mx.utch.cazaobjetos.controller;

import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "server.servlet.session.cookie.secure=false","app.auth.demo-verification=true","DEV_PASSWORD=Caza-Demo-Pruebas-2026!"})
@ActiveProfiles({"test","dev"})
class DemoVerificationControllerTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    HttpClient client;
    String token,header;
    String base() { return "http://127.0.0.1:"+port+"/api/v1/auth"; }
    @BeforeEach void setup() throws Exception {
        db.execute("TRUNCATE app_user,email_outbox CASCADE");
        client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        csrf();
    }
    void csrf() throws Exception {
        var response=client.send(HttpRequest.newBuilder(URI.create(base()+"/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
        var body=json.readTree(response.body());token=body.get("token").asText();header=body.get("headerName").asText();
    }
    int post(String path,String body,boolean withCsrf,boolean form) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base()+path)).header("Content-Type",form?"application/x-www-form-urlencoded":"application/json");
        if(withCsrf)request.header(header,token);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()).statusCode();
    }
    void register() throws Exception {
        assertEquals(202,post("/register","{\"email\":\"a1234567890@utch.edu.mx\",\"password\":\"Demo-Pruebas-2026!\",\"displayName\":\"Alumno de prueba\",\"degree\":\"TI\",\"building\":\"I\"}",true,false));
    }
    @Test void demoButtonActivatesWithoutCodeAndRetainsIdentity() throws Exception {
        register();
        assertEquals(0L,db.queryForObject("SELECT count(*) FROM email_outbox",Long.class));
        assertEquals(401,post("/login","username=a1234567890%40utch.edu.mx&password=Demo-Pruebas-2026!",true,true));
        String body="{\"email\":\"a1234567890@utch.edu.mx\"}";
        assertEquals(403,post("/verify-email/demo",body,false,false));
        db.update("UPDATE email_verification SET expires_at=now()-interval '1 minute'");
        assertEquals(204,post("/verify-email/demo",body,true,false));
        assertEquals(204,post("/verify-email/demo",body,true,false));
        assertEquals(204,post("/login","username=a1234567890%40utch.edu.mx&password=Demo-Pruebas-2026!",true,true));
        var me=client.send(HttpRequest.newBuilder(URI.create(base()+"/me")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals("1234567890",json.readTree(me.body()).get("studentNumber").asText());
        assertEquals("TI",json.readTree(me.body()).get("degree").asText());
        assertEquals("I",json.readTree(me.body()).get("building").asText());
        assertNotNull(db.queryForObject("SELECT consumed_at FROM email_verification",java.sql.Timestamp.class));
    }
    @Test void demoCannotCreateAccountsOrReactivateDisabledVerifiedAccounts() throws Exception {
        String body="{\"email\":\"a1234567890@utch.edu.mx\"}";
        assertEquals(404,post("/verify-email/demo",body,true,false));
        assertEquals(0L,db.queryForObject("SELECT count(*) FROM app_user",Long.class));
        register();assertEquals(204,post("/verify-email/demo",body,true,false));
        db.update("UPDATE app_user SET enabled=false");
        assertEquals(409,post("/verify-email/demo",body,true,false));
        assertFalse(db.queryForObject("SELECT enabled FROM app_user",Boolean.class));
    }
}
