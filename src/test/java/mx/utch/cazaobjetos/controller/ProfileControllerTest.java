package mx.utch.cazaobjetos.controller;

import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=false")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class ProfileControllerTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    HttpClient client;
    UUID student, teacher;
    private static final List<String> OPTIONAL = List.of("alias", "classroom", "groupName", "course", "degree", "building");

    @BeforeEach void setup() throws Exception {
        db.execute("TRUNCATE email_verification,email_outbox,audit_event,claim,lost_report,found_item,app_user CASCADE");
        student = UUID.randomUUID(); teacher = UUID.randomUUID();
        String hash = passwords.encode("profile-test-2026");
        db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES (?,?,?,?,?,true)",
                student, "profile@utch.edu.mx", hash, "Perfil anterior", "STUDENT");
        db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES (?,?,?,?,?,true)",
                teacher, "teacher.profile@utch.edu.mx", hash, "Docente", "TEACHER");
        client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        var csrf = json.readTree(get("/auth/csrf").body());
        var login = HttpRequest.newBuilder(URI.create(base() + "/auth/login"))
                .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=profile%40utch.edu.mx&password=profile-test-2026")).build();
        assertEquals(204, client.send(login, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    String base() { return "http://127.0.0.1:" + port + "/api/v1"; }
    HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void anonymousProfileRequiresSession() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base() + "/auth/me")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
    }

    @Test void accountsWithoutProfileDataStillReturnIdentityAndNullOptionalFields() throws Exception {
        var response = get("/auth/me");
        assertEquals(200, response.statusCode());
        var body = json.readTree(response.body());
        assertEquals(student.toString(), body.get("id").asText());
        assertEquals("profile@utch.edu.mx", body.get("email").asText());
        assertEquals("Perfil anterior", body.get("displayName").asText());
        assertEquals("STUDENT", body.get("role").asText());
        assertNotNull(body.get("createdAt"));
        for (String field : OPTIONAL) assertTrue(body.has(field) && body.get(field).isNull(), field);
        for (String field : List.of("passwordHash", "password", "credentialsVersion", "emailVerifiedAt")) assertFalse(body.has(field), field);
        assertTrue(response.headers().firstValue("cache-control").orElse("").contains("no-store"));
    }

    @Test void profileUsesAuthenticatedIdentityAndDirectoryDoesNotExposeOptionalData() throws Exception {
        db.update("UPDATE app_user SET alias=?,classroom=?,group_name=?,course=?,degree=?,building=? WHERE id=?",
                "Orli", "I-12", "TI-51", "Desarrollo web", "Tecnologías de la información", "I", student);
        db.update("UPDATE app_user SET alias='Privado',classroom='Sala docente' WHERE id=?", teacher);
        var response = get("/auth/me?id=" + teacher + "&userId=" + teacher);
        assertEquals(200, response.statusCode());
        var body = json.readTree(response.body());
        assertEquals(student.toString(), body.get("id").asText());
        assertEquals("Orli", body.get("alias").asText());
        assertEquals("I-12", body.get("classroom").asText());
        assertEquals("TI-51", body.get("groupName").asText());
        assertEquals("Desarrollo web", body.get("course").asText());
        assertEquals("Tecnologías de la información", body.get("degree").asText());
        assertEquals("I", body.get("building").asText());
        var directory = json.readTree(get("/responsibles").body());
        assertEquals(1, directory.get("total").asInt());
        var entry = directory.get("items").get(0);
        assertEquals(teacher.toString(), entry.get("id").asText());
        for (String field : OPTIONAL) assertFalse(entry.has(field), field);
    }

    HttpResponse<String> putProfile(String body, boolean withCsrf) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base() + "/auth/me")).header("Content-Type", "application/json");
        if (withCsrf) {
            var csrf = json.readTree(get("/auth/csrf").body());
            request.header(csrf.get("headerName").asText(), csrf.get("token").asText());
        }
        return client.send(request.PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void savesAndClearsOptionalFieldsWithoutRevokingSessionOrChangingOtherAccounts() throws Exception {
        String body = "{\"displayName\":\"  Jesús Orlando  \",\"alias\":\"  Orli \",\"classroom\":\" I-12 \",\"groupName\":\" TI-51 \",\"course\":\" 5.º cuatrimestre \",\"degree\":\" Tecnologías de la información \",\"building\":\" I \"}";
        var response = putProfile(body, true);
        assertEquals(200, response.statusCode(), response.body());
        var updated = json.readTree(response.body());
        assertEquals("Jesús Orlando", updated.get("displayName").asText());
        assertEquals("Orli", updated.get("alias").asText());
        assertEquals("I-12", updated.get("classroom").asText());
        assertEquals("TI-51", updated.get("groupName").asText());
        assertEquals("5.º cuatrimestre", updated.get("course").asText());
        assertEquals("Tecnologías de la información", updated.get("degree").asText());
        assertEquals("I", updated.get("building").asText());
        assertEquals(200, get("/auth/me").statusCode());
        assertEquals("Docente", db.queryForObject("SELECT display_name FROM app_user WHERE id=?", String.class, teacher));
        assertEquals(0L, db.queryForObject("SELECT credentials_version FROM app_user WHERE id=?", Long.class, student));
        assertEquals(200, putProfile("{\"displayName\":\"Jesús Orlando\",\"alias\":\"   \",\"degree\":\"TI\",\"building\":\"I\"}", true).statusCode());
        var cleared = json.readTree(get("/auth/me").body());
        for (String field : List.of("alias","classroom","groupName","course")) assertTrue(cleared.get(field).isNull(), field);
        assertEquals("TI",cleared.get("degree").asText());
        assertEquals("I",cleared.get("building").asText());
        assertEquals(2L, db.queryForObject("SELECT count(*) FROM audit_event WHERE action='PROFILE_UPDATED' AND actor_id=?", Long.class, student));
    }

    @Test void rejectsInvalidProfileInputsAndMissingCsrfWithoutPartialWrites() throws Exception {
        assertEquals(403, putProfile("{\"displayName\":\"Cambio\"}", false).statusCode());
        for (String body : List.of("{}", "{", "{\"displayName\":\"   \"}", "{\"displayName\":\" A \"}",
                "{\"displayName\":\"" + "x".repeat(121) + "\"}",
                "{\"displayName\":\"Cambio\",\"alias\":\"" + "x".repeat(61) + "\"}",
                "{\"displayName\":\"Cambio\",\"building\":\"" + "x".repeat(81) + "\"}",
                "{\"displayName\":\"Cambio\",\"alias\":\"línea\\nextra\"}")) {
            assertEquals(400, putProfile(body, true).statusCode(), body);
            assertEquals("Perfil anterior", json.readTree(get("/auth/me").body()).get("displayName").asText());
        }
        assertEquals(0L, db.queryForObject("SELECT count(*) FROM audit_event", Long.class));
    }

    @Test void careerAndBuildingCannotBeCleared() throws Exception {
        for(String body:List.of("{\"displayName\":\"Alumno\",\"degree\":\"\",\"building\":\"I\"}",
                "{\"displayName\":\"Alumno\",\"degree\":\"TI\",\"building\":\"   \"}"))
            assertEquals(400,putProfile(body,true).statusCode());
        assertEquals("Perfil anterior",json.readTree(get("/auth/me").body()).get("displayName").asText());
    }

    @Test void studentNumberRecognizesOnlyStudentExampleFormat() throws Exception {
        assertTrue(json.readTree(get("/auth/me").body()).get("studentNumber").isNull());
        db.update("UPDATE app_user SET email='a1234567890@utch.edu.mx' WHERE id=?", student);
        client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        var csrf = json.readTree(get("/auth/csrf").body());
        var login = HttpRequest.newBuilder(URI.create(base() + "/auth/login"))
            .header(csrf.get("headerName").asText(), csrf.get("token").asText())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=a1234567890%40utch.edu.mx&password=profile-test-2026")).build();
        assertEquals(204, client.send(login, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals("1234567890", json.readTree(get("/auth/me").body()).get("studentNumber").asText());
    }

    @Test void profileCannotChangeRoleOrIdentity() throws Exception {
        var csrf = json.readTree(get("/auth/csrf").body());
        var request = HttpRequest.newBuilder(URI.create(base() + "/auth/me"))
                .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"displayName\":\"Cambio\",\"role\":\"SECRETARY\",\"email\":\"other@utch.edu.mx\",\"alias\":\"Cambio\"}")).build();
        assertEquals(400, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        var body = json.readTree(get("/auth/me").body());
        assertEquals("profile@utch.edu.mx", body.get("email").asText());
        assertEquals("STUDENT", body.get("role").asText());
        assertTrue(body.get("alias").isNull());
    }
}
