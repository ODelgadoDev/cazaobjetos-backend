package mx.utch.cazaobjetos.controller;

import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RegistrationControllerTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    HttpClient client;
    String token,header;
    @BeforeEach void setup() throws Exception {
        db.execute("TRUNCATE email_verification,email_outbox,audit_event,claim,lost_report,found_item,app_user CASCADE");
        client = HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        csrf();
    }
    void csrf() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(base()+"/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        var body = json.readTree(response.body()); token=body.get("token").asText(); header=body.get("headerName").asText();
    }
    String base() { return "http://127.0.0.1:"+port+"/api/v1/auth"; }
    int post(String path,String body,boolean withCsrf,boolean form) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base()+path)).header("Content-Type",form ? "application/x-www-form-urlencoded" : "application/json");
        if(withCsrf) request.header(header,token);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()).statusCode();
    }
    String registration() { return "{\"email\":\"a1234567890@utch.edu.mx\",\"password\":\"clave-prueba-123\",\"displayName\":\"Alumno\",\"degree\":\"Tecnologías de la Información\",\"building\":\"I\",\"role\":\"SECRETARY\"}"; }
    void registerAndVerify() throws Exception { registerAndVerify(registration()); }
    void registerAndVerify(String form) throws Exception {
        assertEquals(202,post("/register",form,true,false));
        String code=Arrays.stream(db.queryForObject("SELECT body FROM email_outbox WHERE event_key LIKE 'VERIFY:%' LIMIT 1",String.class).split("\\R")).filter(x->x.matches("[A-Za-z0-9_-]{43}")).findFirst().orElseThrow();
        assertEquals(204,post("/verify-email","{\"token\":\""+code+"\"}",true,false));
    }
    int me(HttpClient current) throws Exception { return current.send(HttpRequest.newBuilder(URI.create(base()+"/me")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode(); }
    @Test void recoveryRevokesAllSessionsAndNewPasswordWorks() throws Exception {
        registerAndVerify(registration().replace("a1234567890@utch.edu.mx","docente.recuperacion@example.org"));
        String login="username=docente.recuperacion%40example.org&password=clave-prueba-123";
        assertEquals(204,post("/login",login,true,true));csrf();HttpClient first=client;
        client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();csrf();
        assertEquals(204,post("/login",login,true,true));csrf();HttpClient second=client;
        client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();csrf();
        assertEquals(403,post("/forgot-password","{\"email\":\"docente.recuperacion@example.org\"}",false,false));
        assertEquals(202,post("/forgot-password","{\"email\":\"absent@utch.edu.mx\"}",true,false));
        assertEquals(202,post("/forgot-password","{\"email\":\"docente.recuperacion@example.org\"}",true,false));
        String code=Arrays.stream(db.queryForObject("SELECT body FROM email_outbox WHERE event_key LIKE 'RESET:%' LIMIT 1",String.class).split("\\R")).filter(x->x.matches("[A-Za-z0-9_-]{43}")).findFirst().orElseThrow();
        assertEquals(204,post("/reset-password","{\"token\":\""+code+"\",\"newPassword\":\"Nueva-12\"}",true,false));
        assertEquals(401,me(first));assertEquals(401,me(second));
        assertEquals(401,post("/login",login,true,true));
        assertEquals(204,post("/login",login.replace("clave-prueba-123","Nueva-12"),true,true));csrf();
        assertEquals(204,post("/change-password","{\"currentPassword\":\"Nueva-12\",\"newPassword\":\"Otra-123\"}",true,false));
        assertEquals(401,me(client));csrf();
        assertEquals(204,post("/login",login.replace("clave-prueba-123","Otra-123"),true,true));
    }
    @Test void disablingAccountOrChangingRoleVersionRevokesSession() throws Exception {
        registerAndVerify();assertEquals(204,post("/login","username=a1234567890%40utch.edu.mx&password=clave-prueba-123",true,true));
        db.update("UPDATE app_user SET role='TEACHER',credentials_version=credentials_version+1");assertEquals(401,me(client));csrf();
        assertEquals(204,post("/login","username=a1234567890%40utch.edu.mx&password=clave-prueba-123",true,true));
        db.update("UPDATE app_user SET enabled=false");assertEquals(401,me(client));
    }
    @Test void completeHttpRegistrationBlocksLoginUntilVerifiedAndCannotEscalateRole() throws Exception {
        assertEquals(202,post("/register",registration(),true,false));
        String login="username=a1234567890%40utch.edu.mx&password=clave-prueba-123";
        assertEquals(401,post("/login",login,true,true));
        String body=db.queryForObject("SELECT body FROM email_outbox LIMIT 1",String.class);
        String code=Arrays.stream(body.split("\\R")).filter(s->s.matches("[A-Za-z0-9_-]{43}")).findFirst().orElseThrow();
        assertEquals(204,post("/verify-email","{\"token\":\""+code+"\"}",true,false));
        assertEquals(204,post("/login",login,true,true)); csrf();
        var me=client.send(HttpRequest.newBuilder(URI.create(base()+"/me")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,me.statusCode());
        assertEquals("STUDENT",json.readTree(me.body()).get("role").asText());
        assertEquals("1234567890",json.readTree(me.body()).get("studentNumber").asText());
        assertEquals("Tecnologías de la Información",json.readTree(me.body()).get("degree").asText());
        assertEquals("I",json.readTree(me.body()).get("building").asText());
        assertFalse(me.body().contains("passwordHash"));
    }
    @Test void externalEmailAccountWithoutMatriculaOrBuildingHasBaseAccess() throws Exception {
        String email="docente.prueba@example.org";
        String form=registration().replace("clave-prueba-123","Prueba1!").replace("a1234567890@utch.edu.mx",email).replace(",\"building\":\"I\"", "");
        assertEquals(202,post("/register",form,true,false));
        var user=db.queryForMap("SELECT role,building,degree FROM app_user WHERE email=?",email);
        assertEquals("STUDENT",user.get("role"));
        assertNull(user.get("building"));
        assertEquals("Tecnologías de la Información",user.get("degree"));
    }
    @Test void demoActivationIsUnavailableInNormalMode() throws Exception {
        var options=client.send(HttpRequest.newBuilder(URI.create(base()+"/registration-options")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,options.statusCode());
        assertFalse(json.readTree(options.body()).get("demoVerification").asBoolean());
        assertEquals(404,post("/verify-email/demo","{\"email\":\"a1234567890@utch.edu.mx\"}",true,false));
    }
    @Test void registrationRequiresCsrfAndValidInputs() throws Exception {
        assertEquals(403,post("/register",registration(),false,false));
        assertEquals(400,post("/register",registration().replace("@utch.edu.mx","@"),true,false));
        for(String invalid : List.of("Corta1!", "Letras!", "Ningundigito!", "SinSimbolo1", "Espacio 1")) {
            assertEquals(400,post("/register",registration().replace("clave-prueba-123",invalid),true,false));
        }
        assertEquals(400,post("/register",registration().replace("Tecnologías de la Información","   "),true,false));
        assertEquals(400,post("/verify-email","{\"token\":\"invalid\"}",true,false));
        assertEquals(0L,db.queryForObject("SELECT count(*) FROM app_user",Long.class));
    }
}
