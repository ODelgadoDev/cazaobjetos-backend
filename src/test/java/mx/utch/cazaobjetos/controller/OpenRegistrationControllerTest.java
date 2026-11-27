package mx.utch.cazaobjetos.controller;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.auth.open-registration=true", "server.servlet.session.cookie.secure=false"})
@ActiveProfiles("test")
class OpenRegistrationControllerTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;

    @BeforeEach void cleanDatabase() {
        db.execute("TRUNCATE app_user,email_outbox CASCADE");
    }

    @Test void openSignupEnablesNewAccountsWithoutSendingEmail() throws Exception {
        var client = client();
        var options = json.readTree(send(client, HttpRequest.newBuilder(uri("/registration-options")).GET().build()).body());
        assertTrue(options.get("openRegistration").asBoolean());

        assertEquals(202, post(client, "/register", "application/json", """
                {"email":"signup@example.org","password":"Prueba-123","displayName":"Persona de prueba","degree":"Otro"}
                """).statusCode());
        assertEquals(204, login(client, "signup", "Prueba-123"));
        assertEquals(200, send(client, HttpRequest.newBuilder(uri("/me")).GET().build()).statusCode());
        assertNull(db.queryForObject("SELECT email_verified_at FROM app_user", java.sql.Timestamp.class));
        assertEquals(0L, db.queryForObject("SELECT count(*) FROM email_verification", Long.class));

        assertEquals(202, post(client(), "/forgot-password", "application/json", """
                {"email":"signup@example.org"}
                """).statusCode());
        assertEquals(0L, db.queryForObject("SELECT count(*) FROM email_outbox", Long.class));
    }

    @Test void onlyCorrectPasswordCanActivateAFormerPendingRegistration() throws Exception {
        UUID id = UUID.randomUUID();
        db.update("""
                INSERT INTO app_user(id,email,password_hash,display_name,role)
                VALUES (?,'pending@example.org',?,'Cuenta pendiente','STUDENT')
                """, id, passwords.encode("Prueba-123"));
        db.update("""
                INSERT INTO email_verification(user_id,token_hash,expires_at,sent_at)
                VALUES (?,repeat('a',64),now()-interval '1 day',now()-interval '2 days')
                """, id);

        var client = client();
        assertEquals(401, login(client, "pending", "Incorrecta-123"));
        assertFalse(db.queryForObject("SELECT enabled FROM app_user WHERE id=?", Boolean.class, id));
        assertEquals(204, login(client, "pending", "Prueba-123"));
        assertTrue(db.queryForObject("SELECT enabled FROM app_user WHERE id=?", Boolean.class, id));
        assertNotNull(db.queryForObject("SELECT consumed_at FROM email_verification WHERE user_id=?", java.sql.Timestamp.class, id));

        db.update("UPDATE app_user SET enabled=false WHERE id=?", id);
        assertEquals(401, login(client(), "pending", "Prueba-123"));
        assertFalse(db.queryForObject("SELECT enabled FROM app_user WHERE id=?", Boolean.class, id));
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + "/api/v1/auth" + path);
    }

    private HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String path, String type, String content) throws Exception {
        var csrf = json.readTree(send(client, HttpRequest.newBuilder(uri("/csrf")).GET().build()).body());
        var request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", type)
                .header(csrf.get("headerName").asText(), csrf.get("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(content)).build();
        return send(client, request);
    }

    private int login(HttpClient client, String user, String password) throws Exception {
        return post(client, "/login", "application/x-www-form-urlencoded",
                "username=" + user + "%40example.org&password=" + password).statusCode();
    }
}
