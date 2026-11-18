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

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.servlet.session.cookie.secure=false","app.push.enabled=true","app.push.worker-enabled=false","app.push.key-file=/tmp/caza-push-http-keys/vapid.properties"})
@ActiveProfiles("test")
@DirtiesContext(classMode=DirtiesContext.ClassMode.BEFORE_CLASS)
class PushControllerTest {
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
    String subscription(UUID expected,String suffix)throws Exception {
        if(java.security.Security.getProvider("BC")==null)java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        var g=java.security.KeyPairGenerator.getInstance("ECDH","BC");g.initialize(org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec("prime256v1"));String key=Base64.getUrlEncoder().withoutPadding().encodeToString(nl.martijndwars.webpush.Utils.encode((org.bouncycastle.jce.interfaces.ECPublicKey)g.generateKeyPair().getPublic()));
        return "{\"expectedUserId\":\""+expected+"\",\"endpoint\":\"https://fcm.googleapis.com/fcm/send/"+suffix+"\",\"keys\":{\"p256dh\":\""+key+"\",\"auth\":\""+Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16])+"\"}}";
    }
    @Test void configAndPreferencesRequireSessionAndAllWritesRequireCsrf()throws Exception {
        assertEquals(401,get(HttpClient.newHttpClient(),"/push/config").statusCode());assertEquals(401,get(HttpClient.newHttpClient(),"/push/preferences").statusCode());
        var config=get(owner,"/push/config");assertEquals(200,config.statusCode());var value=json.readTree(config.body());assertTrue(value.get("enabled").asBoolean());assertEquals(87,value.get("applicationServerKey").asText().length());assertFalse(new String(config.body()).contains("private"));assertTrue(config.headers().firstValue("cache-control").orElse("").contains("no-store"));
        assertEquals(403,post(owner,"/push/subscriptions",subscription(ownerId,"csrf"),false,null).statusCode());assertEquals(0L,db.queryForObject("SELECT count(*) FROM push_subscription",Long.class));
    }
    @Test void registrationIsPrivateAndDangerousEndpointsOrMalformedKeysAreRejected()throws Exception {
        String body=subscription(ownerId,"valid");var saved=post(owner,"/push/subscriptions",body,true,null);assertEquals(200,saved.statusCode(),new String(saved.body()));String id=json.readTree(saved.body()).get("id").asText();assertEquals(ownerId.toString(),json.readTree(saved.body()).get("userId").asText());assertFalse(new String(saved.body()).contains("endpoint"));assertEquals(43,json.readTree(saved.body()).get("bindingToken").asText().length());
        for(String endpoint:List.of("https://127.0.0.1/x","https://fcm.googleapis.com.evil.test/x","http://fcm.googleapis.com/x","https://fcm.googleapis.com:8443/x","https://user@fcm.googleapis.com/x","https://fcm.googleapis.com/x#frag"))assertEquals(400,post(owner,"/push/subscriptions",body.replace("https://fcm.googleapis.com/fcm/send/valid",endpoint),true,null).statusCode());
        assertEquals(409,post(other,"/push/subscriptions",body,true,null).statusCode());assertEquals(400,post(owner,"/push/subscriptions",body.replace("\"auth\":\"","\"auth\":\"x"),true,null).statusCode());
        assertEquals(404,post(other,"/push/test","{\"subscriptionId\":\""+id+"\"}",true,null).statusCode());
    }
    @Test void successfulLogoutRevokesOnlyTheCurrentBrowserSession()throws Exception {
        String id=json.readTree(post(owner,"/push/subscriptions",subscription(ownerId,"session-one"),true,null).body()).get("id").asText();var second=login("owner");String otherId=json.readTree(post(second,"/push/subscriptions",subscription(ownerId,"session-two"),true,null).body()).get("id").asText();assertEquals(204,post(owner,"/auth/logout","",true,null).statusCode());
        assertFalse(db.queryForObject("SELECT active FROM push_subscription WHERE id=?",Boolean.class,UUID.fromString(id)));assertTrue(db.queryForObject("SELECT active FROM push_subscription WHERE id=?",Boolean.class,UUID.fromString(otherId)));
    }
    @Test void loggingIntoAnotherAccountRevokesThePreviousSessionBinding()throws Exception {
        String id=json.readTree(post(owner,"/push/subscriptions",subscription(ownerId,"before-account-switch"),true,null).body()).get("id").asText();
        var req=request(owner,"/auth/login",true).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("username=other%40utch.edu.mx&password=dossier-test-2026")).build();assertEquals(204,owner.send(req,HttpResponse.BodyHandlers.discarding()).statusCode());
        assertFalse(db.queryForObject("SELECT active FROM push_subscription WHERE id=?",Boolean.class,UUID.fromString(id)));
    }
    @Test void preferencesAndTestMessageUseAuthenticatedAccountAndValidation()throws Exception {
        var preference=request(owner,"/push/preferences",true).header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString("{\"dailyReminders\":2}")).build();assertEquals(200,owner.send(preference,HttpResponse.BodyHandlers.discarding()).statusCode());assertEquals(2,json.readTree(get(owner,"/push/preferences").body()).get("dailyReminders").asInt());assertEquals(1,json.readTree(get(other,"/push/preferences").body()).get("dailyReminders").asInt());
        for(String body:List.of("{}","{\"dailyReminders\":3}")){var req=request(owner,"/push/preferences",true).header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString(body)).build();assertEquals(400,owner.send(req,HttpResponse.BodyHandlers.discarding()).statusCode());}
        String id=json.readTree(post(owner,"/push/subscriptions",subscription(ownerId,"test-message"),true,null).body()).get("id").asText();String body="{\"subscriptionId\":\""+id+"\"}";assertEquals(202,post(owner,"/push/test",body,true,null).statusCode());assertEquals(429,post(owner,"/push/test",body,true,null).statusCode());
        var delete=request(other,"/push/subscriptions/"+id,true).DELETE().build();assertEquals(404,other.send(delete,HttpResponse.BodyHandlers.discarding()).statusCode());
    }
}
