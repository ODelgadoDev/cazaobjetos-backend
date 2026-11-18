package mx.utch.cazaobjetos.controller;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
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
class ProfilePhotoControllerTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    HttpClient owner, other;
    UUID ownerId, otherId;

    @BeforeEach void setup() throws Exception {
        db.execute("TRUNCATE app_user CASCADE");
        ownerId = UUID.randomUUID(); otherId = UUID.randomUUID();
        String hash = passwords.encode("avatar-test-2026");
        db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES (?,?,?,?,?,true)", ownerId, "avatar@utch.edu.mx", hash, "Avatar", "STUDENT");
        db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES (?,?,?,?,?,true)", otherId, "other.avatar@utch.edu.mx", hash, "Otro", "TEACHER");
        owner = login("avatar@utch.edu.mx"); other = login("other.avatar@utch.edu.mx");
    }
    String base() { return "http://127.0.0.1:" + port + "/api/v1/auth/me"; }
    HttpResponse<byte[]> get(HttpClient client, String suffix) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + suffix)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    HttpClient login(String email) throws Exception {
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        var token = csrf(client);
        var request = HttpRequest.newBuilder(URI.create(base().replace("/me", "/login")))
                .header(token.get("headerName").asText(), token.get("token").asText())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password=avatar-test-2026")).build();
        assertEquals(204, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
        return client;
    }
    tools.jackson.databind.JsonNode csrf(HttpClient client) throws Exception {
        return json.readTree(client.send(HttpRequest.newBuilder(URI.create(base().replace("/me", "/csrf"))).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
    }
    byte[] image(int width, int height, Color color) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics(); graphics.setColor(color); graphics.fillRect(0, 0, width, height); graphics.dispose();
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes); return bytes.toByteArray();
    }
    HttpResponse<byte[]> upload(HttpClient client, byte[] bytes, boolean withCsrf, String suffix) throws Exception {
        String boundary = "avatar-test-boundary";
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"avatar.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(bytes); body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create(base() + "/photo" + suffix)).header("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (withCsrf) { var token = csrf(client); request.header(token.get("headerName").asText(), token.get("token").asText()); }
        return client.send(request.PUT(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    HttpResponse<byte[]> delete(HttpClient client, boolean withCsrf) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base() + "/photo"));
        if (withCsrf) { var token = csrf(client); request.header(token.get("headerName").asText(), token.get("token").asText()); }
        return client.send(request.DELETE().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    @Test void photoIsNormalizedPrivateAndReplacementDoesNotAccumulateRows() throws Exception {
        assertTrue(json.readTree(get(owner, "").body()).get("avatarVersion").isNull());
        assertEquals(404, get(owner, "/photo").statusCode());
        var bytes = new ByteArrayOutputStream(); bytes.write(image(800, 400, Color.GREEN)); bytes.write("PRIVATE-TRAILING-DATA".getBytes());
        var upload = upload(owner, bytes.toByteArray(), true, "");
        assertEquals(200, upload.statusCode(), new String(upload.body()));
        String firstVersion = json.readTree(upload.body()).get("avatarVersion").asText();
        assertEquals(firstVersion, json.readTree(get(owner, "").body()).get("avatarVersion").asText());
        var content = get(owner, "/photo?v=" + firstVersion);
        assertEquals(200, content.statusCode());
        assertEquals("image/jpeg", content.headers().firstValue("content-type").orElseThrow());
        assertTrue(content.headers().firstValue("cache-control").orElse("").contains("no-store"));
        var decoded = ImageIO.read(new ByteArrayInputStream(content.body()));
        assertEquals(512, decoded.getWidth()); assertEquals(512, decoded.getHeight());
        assertFalse(new String(content.body(), StandardCharsets.ISO_8859_1).contains("PRIVATE-TRAILING-DATA"));
        assertEquals(401, get(HttpClient.newHttpClient(), "/photo").statusCode());
        var next = upload(owner, image(300, 500, Color.BLUE), true, "");
        assertEquals(200, next.statusCode());
        assertNotEquals(firstVersion, json.readTree(next.body()).get("avatarVersion").asText());
        assertEquals(1L, db.queryForObject("SELECT count(*) FROM user_profile_photo", Long.class));
        assertEquals(0L, db.queryForObject("SELECT count(*) FROM publication_photo", Long.class));
    }
    @Test void invalidFilesAndMissingCsrfKeepPreviousPhotoIntact() throws Exception {
        assertEquals(200, upload(owner, image(20, 20, Color.ORANGE), true, "").statusCode());
        String version = json.readTree(get(owner, "").body()).get("avatarVersion").asText();
        assertEquals(403, upload(owner, image(10, 10, Color.RED), false, "").statusCode());
        assertEquals(400, upload(owner, new byte[0], true, "").statusCode());
        assertEquals(415, upload(owner, "<html>fake image</html>".getBytes(), true, "").statusCode());
        assertEquals(413, upload(owner, new byte[5 * 1024 * 1024 + 1], true, "").statusCode());
        assertEquals(400, upload(owner, image(6001, 1, Color.WHITE), true, "").statusCode());
        assertEquals(version, json.readTree(get(owner, "").body()).get("avatarVersion").asText());
        assertEquals(1L, db.queryForObject("SELECT count(*) FROM user_profile_photo", Long.class));
        assertEquals(403, delete(owner, false).statusCode());
    }
    @Test void eachAccountOnlyReadsReplacesAndDeletesItsOwnPhoto() throws Exception {
        assertEquals(200, upload(owner, image(100, 100, Color.GREEN), true, "").statusCode());
        String ownerVersion = json.readTree(get(owner, "").body()).get("avatarVersion").asText();
        assertEquals(404, get(other, "/photo?userId=" + ownerId).statusCode());
        assertEquals(200, upload(other, image(100, 100, Color.BLUE), true, "?userId=" + ownerId).statusCode());
        assertEquals(ownerVersion, json.readTree(get(owner, "").body()).get("avatarVersion").asText());
        assertEquals(2L, db.queryForObject("SELECT count(*) FROM user_profile_photo", Long.class));
        assertEquals(404, get(other, "/photo/" + ownerId).statusCode());
        assertEquals(200, delete(other, true).statusCode());
        assertEquals(200, delete(other, true).statusCode());
        assertTrue(json.readTree(get(other, "").body()).get("avatarVersion").isNull());
        assertEquals(404, get(other, "/photo").statusCode());
        assertEquals(200, get(owner, "/photo").statusCode());
        assertEquals(1L, db.queryForObject("SELECT count(*) FROM user_profile_photo", Long.class));
    }
    @Test void disabledSessionCannotReadStoredPhoto() throws Exception {
        assertEquals(200, upload(owner, image(10, 10, Color.GREEN), true, "").statusCode());
        db.update("UPDATE app_user SET enabled=false WHERE id=?", ownerId);
        assertEquals(401, get(owner, "/photo").statusCode());
    }
}
