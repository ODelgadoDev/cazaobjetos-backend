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
class NotificationControllerTest {
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
    @Test void notificationRoutesRequireSessionCsrfAndRecipientScope() throws Exception {
        for(String suffix:List.of("", "/summary"))assertEquals(401,get(HttpClient.newHttpClient(),"/notifications"+suffix).statusCode());
        var rows=json.readTree(get(finder,"/notifications").body());String id=rows.get("items").get(0).get("id").asText();
        assertEquals(403,post(finder,"/notifications/"+id+"/read","",false,null).statusCode());assertEquals(403,post(finder,"/notifications/clear-read","",false,null).statusCode());
        assertEquals(404,post(other,"/notifications/"+id+"/read","",true,null).statusCode());assertEquals(400,post(finder,"/notifications/not-uuid/read","",true,null).statusCode());
        for(String query:List.of("page=-1","pageSize=101","filter=INVALID"))assertEquals(400,get(finder,"/notifications?"+query).statusCode());
        var outsider=json.readTree(get(other,"/notifications?recipientId="+finderId).body());assertEquals(0,outsider.get("total").asInt());
    }
    @Test void readsAndCleanupPreservePendingAndAreNeverTriggeredByGets() throws Exception {
        var summary=json.readTree(get(finder,"/notifications/summary").body());assertEquals(2,summary.get("unread").asInt());
        var list=get(finder,"/notifications?filter=UNREAD&pageSize=1");assertEquals(200,list.statusCode());var rows=json.readTree(list.body());assertTrue(rows.get("hasNext").asBoolean());String id=rows.get("items").get(0).get("id").asText();assertTrue(rows.get("items").get(0).get("readAt").isNull());
        assertEquals(2,json.readTree(get(finder,"/notifications/summary").body()).get("unread").asInt());
        var read=post(finder,"/notifications/"+id+"/read","",true,null);assertEquals(200,read.statusCode());String readAt=json.readTree(read.body()).get("readAt").asText();assertEquals(readAt,json.readTree(post(finder,"/notifications/"+id+"/read","",true,null).body()).get("readAt").asText());
        var clear=post(finder,"/notifications/clear-read","",true,null);assertEquals(200,clear.statusCode());assertEquals(1,json.readTree(clear.body()).get("cleared").asInt());assertEquals(1,json.readTree(get(finder,"/notifications/summary").body()).get("unread").asInt());
        assertEquals(1,json.readTree(get(owner,"/notifications/summary").body()).get("unread").asInt());assertEquals(0,json.readTree(post(other,"/notifications/clear-read","",true,null).body()).get("cleared").asInt());
    }
    @Test void commentingCreatesOneGenericNoticeAndClosedCasesKeepTheirDestination() throws Exception {
        String path="/claims/"+claim+"/comments",body="{\"body\":\"SECRETO que solo se ve en el expediente\"}";
        assertEquals(201,post(owner,path,body,true,"notify-http-retry-01").statusCode());assertEquals(201,post(owner,path,body,true,"notify-http-retry-01").statusCode());
        var list=get(finder,"/notifications");assertFalse(new String(list.body(),StandardCharsets.UTF_8).contains("SECRETO"));assertEquals(3,json.readTree(list.body()).get("total").asInt());
        assertEquals(204,post(finder,"/claims/"+claim+"/accept","",true,null).statusCode());assertEquals(204,post(owner,"/claims/"+claim+"/complete","",true,null).statusCode());
        var notices=json.readTree(get(finder,"/notifications").body()).get("items");String id=notices.get(0).get("id").asText();assertEquals(200,post(finder,"/notifications/"+id+"/read","",true,null).statusCode());assertEquals(200,get(finder,"/claims/"+claim).statusCode());
    }
}
