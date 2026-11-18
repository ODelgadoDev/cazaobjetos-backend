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
class ClaimDossierControllerTest {
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
            out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+(requestBody==null?"file":"files")+"\"; filename=\"private.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes());out.write(file);out.write("\r\n".getBytes());
        }
        out.write(("--"+boundary+"--\r\n").getBytes());var req=request(owner,path,csrf).header("Content-Type","multipart/form-data; boundary="+boundary);
        if(key!=null)req.header("Idempotency-Key",key);
        return owner.send(req.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    @Test void privateRoutesRequireSessionAndParticipantPermission() throws Exception {
        for(String suffix:List.of("/activity","/evidence")) {
            assertEquals(401,get(HttpClient.newHttpClient(),"/claims/"+claim+suffix).statusCode());
            assertEquals(404,get(other,"/claims/"+claim+suffix).statusCode());
            assertEquals(200,get(owner,"/claims/"+claim+suffix).statusCode());
        }
        assertEquals(403,post(owner,"/claims/"+claim+"/comments","{\"body\":\"Hola\"}",false,null).statusCode());
        assertEquals(404,post(other,"/claims/"+claim+"/comments","{\"body\":\"Hola\"}",true,null).statusCode());
        assertEquals(400,post(owner,"/claims/"+claim+"/comments","{\"body\":\" \"}",true,null).statusCode());
        assertEquals(400,get(owner,"/claims/"+claim+"/activity?pageSize=101").statusCode());
    }
    @Test void commentContractSupportsRetriesAndClosedReadOnlyHistory() throws Exception {
        String route="/claims/"+claim+"/comments",body="{\"body\":\"¿En qué horario puedo pasar?\"}";
        var first=post(owner,route,body,true,"comment-http-001");assertEquals(201,first.statusCode());
        assertEquals(json.readTree(first.body()).get("id").asText(),json.readTree(post(owner,route,body,true,"comment-http-001").body()).get("id").asText());
        assertEquals(409,post(owner,route,body.replace("horario","lugar"),true,"comment-http-001").statusCode());
        assertEquals(204,post(finder,"/claims/"+claim+"/accept","",true,null).statusCode());
        assertEquals(204,post(owner,"/claims/"+claim+"/complete","",true,null).statusCode());
        assertEquals(409,post(owner,route,body,true,"comment-http-002").statusCode());
        var history=get(owner,"/claims/"+claim+"/activity");assertEquals(200,history.statusCode());assertEquals(4,json.readTree(history.body()).get("total").asInt());
        assertFalse(json.readTree(get(owner,"/claims/"+claim).body()).get("canContribute").asBoolean());
    }
    @Test void evidenceContentIsScopedAndNeverPubliclyCached() throws Exception {
        String route="/claims/"+claim+"/evidence";
        assertEquals(403,multipart(route,null,List.of(image(false)),false,null).statusCode());
        var uploaded=multipart(route,null,List.of(image(false)),true,"evidence-http-001");assertEquals(201,uploaded.statusCode());
        String id=json.readTree(uploaded.body()).get("id").asText();var content=get(owner,route+"/"+id);
        assertEquals(200,content.statusCode());assertEquals("image/png",content.headers().firstValue("content-type").orElseThrow());
        assertTrue(content.headers().firstValue("cache-control").orElse("").contains("no-store"));assertEquals("nosniff",content.headers().firstValue("x-content-type-options").orElseThrow());
        assertEquals(404,get(other,route+"/"+id).statusCode());assertEquals(200,get(finder,route+"/"+id).statusCode());
        assertEquals(204,post(finder,"/claims/"+claim+"/reject","{\"reason\":\"Los detalles no coinciden con el objeto.\"}",true,null).statusCode());
        assertEquals(200,get(owner,route+"/"+id).statusCode());assertEquals(409,multipart(route,null,List.of(image(false)),true,null).statusCode());
        assertTrue(new String(get(owner,"/claims/"+claim+"/activity").body(),StandardCharsets.UTF_8).contains("Los detalles no coinciden"));
    }
    @Test void initialEvidenceBatchLargerThanSixMiBCommitsOnceAndInvalidBatchCreatesNothing() throws Exception {
        UUID item=items.createFound(finderId,new ReportRequestDto("Otro objeto","Descripción",Category.OTHER,"I",LocalDate.now()));
        String route="/found-items/"+item+"/claims",body="{\"privateDescription\":\"Tiene señales privadas que puedo demostrar\"}";
        long before=db.queryForObject("SELECT count(*) FROM claim",Long.class);
        assertEquals(415,multipart(route,body,List.of(image(false),"fake".getBytes()),true,"atomic-http-001").statusCode());
        assertEquals(before,db.queryForObject("SELECT count(*) FROM claim",Long.class));
        byte[] large=image(true);assertTrue(large.length*2>6*1024*1024);
        var first=multipart(route,body,List.of(large,large),true,"atomic-http-001");assertEquals(201,first.statusCode(),new String(first.body()));
        var repeat=multipart(route,body,List.of(large,large),true,"atomic-http-001");assertEquals(201,repeat.statusCode());
        String id=json.readTree(first.body()).get("id").asText();assertEquals(id,json.readTree(repeat.body()).get("id").asText());
        assertEquals(2,json.readTree(get(owner,"/claims/"+id+"/evidence").body()).size());
        assertEquals(before+1,db.queryForObject("SELECT count(*) FROM claim",Long.class));
    }
}
