package mx.utch.cazaobjetos.controller;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.service.ItemService;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="server.servlet.session.cookie.secure=false")
@ActiveProfiles("test")
@DirtiesContext(classMode=DirtiesContext.ClassMode.BEFORE_CLASS)
class FoundPublicationControllerTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired ItemService items;
    @Autowired ObjectMapper json;
    HttpClient owner,finder,other;
    UUID finderId,ownerId,found,claim;
    @BeforeEach void setup() throws Exception {
        db.execute("TRUNCATE app_user CASCADE");String hash=passwords.encode("dossier-test-2026");
        finderId=user("finder",hash);ownerId=user("owner",hash);user("other",hash);
        found=items.createFound(finderId,new ReportRequestDto("Objeto HTTP","Descripción pública",Category.OTHER,"I",LocalDate.now()));
        claim=items.claim(ownerId,found,new ClaimRequestDto("Descripción privada de propiedad",null));
        owner=login("owner");finder=login("finder");other=login("other");
    }
    UUID user(String name,String hash) {var id=UUID.randomUUID();db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES(?,?,?,?,?,true)",id,name+"@utch.edu.mx",hash,name,"STUDENT");return id;}
    String base() {return "http://127.0.0.1:"+port+"/api/v1";}
    HttpResponse<byte[]> get(HttpClient client,String path) throws Exception {return client.send(HttpRequest.newBuilder(URI.create(base()+path)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());}
    HttpRequest.Builder request(HttpClient client,String path,boolean csrf) throws Exception {
        var req=HttpRequest.newBuilder(URI.create(base()+path));
        if(csrf) {var token=json.readTree(get(client,"/auth/csrf").body());req.header(token.get("headerName").asText(),token.get("token").asText());}
        return req;
    }
    HttpClient login(String name) throws Exception {
        var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        var req=request(client,"/auth/login",true).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("username="+name+"%40utch.edu.mx&password=dossier-test-2026")).build();
        assertEquals(204,client.send(req,HttpResponse.BodyHandlers.discarding()).statusCode());return client;
    }
    HttpResponse<byte[]> post(HttpClient client,String path,String body,boolean csrf,String key) throws Exception {
        var req=request(client,path,csrf).header("Content-Type","application/json");if(key!=null)req.header("Idempotency-Key",key);
        return client.send(req.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    byte[] image(boolean large) throws Exception {
        int side=large?1024:8;var image=new BufferedImage(side,side,BufferedImage.TYPE_INT_RGB);
        if(large) {var random=new Random(42);for(int y=0;y<side;y++)for(int x=0;x<side;x++)image.setRGB(x,y,random.nextInt(1<<24));}
        var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
    }
    HttpResponse<byte[]> multipart(String path,String requestBody,List<byte[]> files,boolean csrf,String key) throws Exception {
        String boundary="private-evidence-boundary";var out=new ByteArrayOutputStream();
        if(requestBody!=null)out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"request\"\r\nContent-Type: application/json\r\n\r\n"+requestBody+"\r\n").getBytes(StandardCharsets.UTF_8));
        for(byte[] file:files) {
            out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+"file"+"\"; filename=\"private.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes());out.write(file);out.write("\r\n".getBytes());
        }
        out.write(("--"+boundary+"--\r\n").getBytes());var req=request(owner,path,csrf).header("Content-Type","multipart/form-data; boundary="+boundary);
        if(key!=null)req.header("Idempotency-Key",key);
        return owner.send(req.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    String publication(UUID source) {return "{\"report\":{\"title\":\"Mochila\",\"description\":\"Descripción visible\",\"category\":\"OTHER\",\"location\":\"I-15\",\"occurredOn\":\""+LocalDate.now()+"\"},\"sourceLostReportId\":"+(source==null?"null":"\""+source+"\"")+",\"custody\":{\"custody\":\"WITH_FINDER\"}}";}
    @Test void publicationRequiresCsrfAndValidNestedFieldsButPhotoIsOptional() throws Exception {
        String route="/found-items/publish",body=publication(null);long before=db.queryForObject("SELECT count(*) FROM found_item",Long.class);
        assertEquals(403,multipart(route,body,List.of(image(false)),false,null).statusCode());assertEquals(415,multipart(route,body,List.of("fake".getBytes()),true,null).statusCode());assertEquals(400,multipart(route,body.replace("Mochila",""),List.of(image(false)),true,null).statusCode());
        assertEquals(before,db.queryForObject("SELECT count(*) FROM found_item",Long.class));
        var created=multipart(route,body,List.of(),true,"without-photo-001");
        assertEquals(201,created.statusCode());
        String id=json.readTree(created.body()).get("id").asText();
        assertEquals(0,json.readTree(get(owner,"/found-items/"+id+"/photos").body()).size());
    }
    @Test void multipartPublicationIsAtomicAndRetryReturnsSameIdentifier() throws Exception {
        String body=publication(null);var first=multipart("/found-items/publish",body,List.of(image(false)),true,"publish-http-001");assertEquals(201,first.statusCode(),new String(first.body()));String id=json.readTree(first.body()).get("id").asText();assertEquals(id,json.readTree(multipart("/found-items/publish",body,List.of(image(false)),true,"publish-http-001").body()).get("id").asText());
        assertEquals(1,json.readTree(get(owner,"/found-items/"+id+"/photos").body()).size());assertTrue(json.readTree(get(owner,"/found-items/"+id).body()).get("sourceLostReportId").isNull());
        assertEquals(409,multipart("/found-items/publish",body.replace("Mochila","Otra"),List.of(image(false)),true,"publish-http-001").statusCode());
    }
    @Test void sourceIsPersistentAndItsOwnerHasAPrivateMatchesList() throws Exception {
        UUID source=items.createLost(finderId,new ReportRequestDto("Mochila perdida","Ficha pública",Category.OTHER,"I",LocalDate.now()));
        var response=multipart("/found-items/publish",publication(source),List.of(image(false)),true,"source-http-001");assertEquals(201,response.statusCode());String id=json.readTree(response.body()).get("id").asText();assertEquals(source.toString(),json.readTree(get(owner,"/found-items/"+id).body()).get("sourceLostReportId").asText());
        var list=get(finder,"/lost-reports/"+source+"/matches");assertEquals(200,list.statusCode());assertEquals(id,json.readTree(list.body()).get("items").get(0).get("id").asText());assertEquals(404,get(other,"/lost-reports/"+source+"/matches").statusCode());assertEquals(400,get(finder,"/lost-reports/"+source+"/matches?pageSize=101").statusCode());
        var notices=json.readTree(get(finder,"/notifications").body()).get("items");assertTrue(notices.toString().contains("POSSIBLE_MATCH"));
    }
    @Test void sourceClosedDuringEditingRejectsEverythingWithoutFalseMatch() throws Exception {
        UUID source=items.createLost(finderId,new ReportRequestDto("Perdido","Descripción",Category.OTHER,"I",LocalDate.now()));items.recoverLost(finderId,source);long before=db.queryForObject("SELECT count(*) FROM found_item",Long.class);
        assertEquals(409,multipart("/found-items/publish",publication(source),List.of(image(false)),true,"closed-http-001").statusCode());assertEquals(before,db.queryForObject("SELECT count(*) FROM found_item",Long.class));
        assertEquals(0L,db.queryForObject("SELECT count(*) FROM user_notification WHERE kind='POSSIBLE_MATCH'",Long.class));
    }
}
