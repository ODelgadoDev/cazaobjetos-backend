package mx.utch.cazaobjetos.controller;

import java.net.*;
import java.net.http.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"server.servlet.session.cookie.secure=false","app.cors.allowed-origins=http://localhost:5173"})
@ActiveProfiles("test")
class CatalogControllerTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    HttpClient client;
    @BeforeEach void setup() throws Exception {
        db.execute("TRUNCATE email_verification,email_outbox,audit_event,claim,lost_report,found_item,app_user CASCADE");
        UUID user=UUID.randomUUID();
        db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES (?,?,?,?,?,true)",user,"catalog@utch.edu.mx",passwords.encode("catalog-test-2026"),"Catálogo","STUDENT");
        db.update("INSERT INTO found_item(id,reporter_id,responsible_id,title,description,category,found_location,found_on) VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID(),user,user,"Mochila negra","Con cierre gris","CLOTHING","Edificio I",java.time.LocalDate.of(2026,10,1));
        client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        var csrf=json.readTree(get("/auth/csrf").body());
        var login=HttpRequest.newBuilder(URI.create(base()+"/auth/login"))
            .header(csrf.get("headerName").asText(),csrf.get("token").asText())
            .header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("username=catalog%40utch.edu.mx&password=catalog-test-2026")).build();
        assertEquals(204,client.send(login,HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    String base(){return "http://127.0.0.1:"+port+"/api/v1";}
    HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base()+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> mutate(String method,String path,String body,boolean withCsrf) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base()+path)).header("Content-Type","application/json");
        if(withCsrf) { var token=json.readTree(get("/auth/csrf").body());request.header(token.get("headerName").asText(),token.get("token").asText()); }
        return client.send(request.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void publicationEndpointsValidateCsrfAndPreserveWithdrawnHistory() throws Exception {
        String body="{\"title\":\"Llaves\",\"description\":\"Llavero azul\",\"category\":\"KEYS\",\"location\":\"I-3\",\"occurredOn\":\"2026-10-01\"}";
        for(String route:new String[]{"/found-items","/lost-reports"}) {
            var created=mutate("POST",route,body,true);assertEquals(201,created.statusCode());
            String path=route+"/"+json.readTree(created.body()).get("id").asText();
            assertEquals(403,mutate("PUT",path,body,false).statusCode());
            assertEquals(400,mutate("PUT",path,"{}",true).statusCode());
            assertEquals(204,mutate("PUT",path,body.replace("Llaves","Llaves nuevas"),true).statusCode());
            assertEquals("Llaves nuevas",json.readTree(get(path).body()).get("title").asText());
            assertEquals(204,mutate("POST",path+"/withdraw","",true).statusCode());
            assertEquals(404,get(path).statusCode());
            assertEquals(1,json.readTree(get(route+"/mine?status=WITHDRAWN").body()).get("total").asInt());
            assertEquals(0,json.readTree(get(route+"?status=WITHDRAWN").body()).get("total").asInt());
        }
    }
    HttpResponse<byte[]> upload(String path,byte[] bytes,boolean csrf) throws Exception {
        String boundary="photo-test-boundary";
        var out=new java.io.ByteArrayOutputStream();
        out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes());
        out.write(bytes);out.write(("\r\n--"+boundary+"--\r\n").getBytes());
        var req=HttpRequest.newBuilder(URI.create(base()+path)).header("Content-Type","multipart/form-data; boundary="+boundary);
        if(csrf) { var token=json.readTree(get("/auth/csrf").body());req.header(token.get("headerName").asText(),token.get("token").asText()); }
        return client.send(req.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    @Test void photoHttpContractAndMultipartLimits() throws Exception {
        var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3,3,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);
        String parent=json.readTree(get("/found-items/mine").body()).get("items").get(0).get("id").asText();
        String route="/found-items/"+parent+"/photos";
        assertEquals(403,upload(route,out.toByteArray(),false).statusCode());
        assertEquals(415,upload(route,"invalid".getBytes(),true).statusCode());
        assertEquals(413,upload(route,new byte[5*1024*1024+1],true).statusCode());
        var uploaded=upload(route,out.toByteArray(),true);assertEquals(201,uploaded.statusCode());
        String id=json.readTree(uploaded.body()).get("id").asText();
        var response=client.send(HttpRequest.newBuilder(URI.create(base()+route+"/"+id)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200,response.statusCode());assertEquals("image/png",response.headers().firstValue("content-type").orElseThrow());
        assertEquals("no-store",response.headers().firstValue("cache-control").orElseThrow());
        assertNotNull(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(response.body())));
        assertEquals(1,json.readTree(get(route).body()).size());
        assertEquals(204,mutate("DELETE",route+"/"+id,"",true).statusCode());
        assertEquals(404,get(route+"/"+id).statusCode());
        assertEquals(401,HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base()+route)).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test void directoryAndClaimsHttpValidationAndPrivacy() throws Exception {
        assertEquals(200,get("/responsibles").statusCode());
        assertEquals(200,get("/claims/search").statusCode());
        assertEquals(404,get("/claims/"+UUID.randomUUID()).statusCode());
        assertTrue(json.readTree(get("/claims").body()).isArray());
        for(String path:new String[]{"/responsibles?role=STUDENT","/responsibles?role=BAD","/responsibles?page=-1","/responsibles?pageSize=101","/responsibles?q="+"a".repeat(201),"/claims/search?view=BAD","/claims/search?status=BAD","/claims/search?foundItemId=bad","/claims/search?pageSize=0","/claims/search?page=10001"})
            assertEquals(400,get(path).statusCode(),path);
        for(String path:new String[]{"/responsibles","/claims/search","/claims/"+UUID.randomUUID()})
            assertEquals(401,HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base()+path)).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        assertFalse(get("/responsibles").body().contains("password"));
    }
    @Autowired org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;
    @Test void contractDocumentsEveryControllerAndCorsAcceptsOnlyConfiguredOrigin() throws Exception {
        HttpClient anonymous=HttpClient.newHttpClient();
        var contract=anonymous.send(HttpRequest.newBuilder(URI.create(base()+"/openapi.json")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,contract.statusCode());var paths=json.readTree(contract.body()).get("paths");
        mappings.getHandlerMethods().forEach((mapping,handler)->{
            for(String path:mapping.getPatternValues()) {
                if(!path.startsWith("/api/v1"))continue;
                for(var method:mapping.getMethodsCondition().getMethods()) {
                    if(path.endsWith("/{action}")) {
                        for(String action:new String[]{"accept","reject","cancel","complete"})assertTrue(paths.get(path.substring(7).replace("{action}",action)).has("post"));
                    } else assertTrue(paths.get(path.substring(7)).has(method.name().toLowerCase()),path);
                }
            }
        });
        assertEquals(200,anonymous.send(HttpRequest.newBuilder(URI.create(base()+"/health")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        for(String origin:new String[]{"http://localhost:5173","https://untrusted.example"}) {
            var response=anonymous.send(HttpRequest.newBuilder(URI.create(base()+"/found-items"))
                .header("Origin",origin).header("Access-Control-Request-Method","POST")
                .header("Access-Control-Request-Headers","Content-Type,X-CSRF-TOKEN,Idempotency-Key")
                .method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(origin.contains("localhost")?200:403,response.statusCode());
            if(response.statusCode()==200) { assertEquals(origin,response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());assertEquals("true",response.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow()); }
        }
    }
    @Test void httpCreationRetriesReturnSameId() throws Exception {
        var token=json.readTree(get("/auth/csrf").body());
        String body="{\"title\":\"Objeto\",\"description\":\"Descripción\",\"category\":\"OTHER\",\"location\":\"I\",\"occurredOn\":\"2026-10-01\"}";
        String first=null;
        for(int n=0;n<2;n++) {
            var response=client.send(HttpRequest.newBuilder(URI.create(base()+"/found-items"))
                .header(token.get("headerName").asText(),token.get("token").asText()).header("Idempotency-Key","http-retry-001")
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(201,response.statusCode());if(n==0)first=response.body();else assertEquals(first,response.body());
        }
    }
    @Test void returnsEnvelopeDefaultsAndCombinedFilters() throws Exception {
        var response=get("/found-items?q=MOCHILA&category=CLOTHING&location=Edificio&status=AVAILABLE&custody=WITH_FINDER&dateFrom=2026-10-01&dateTo=2026-10-01&pageSize=1&sort=OLDEST");
        assertEquals(200,response.statusCode());var body=json.readTree(response.body());
        assertEquals(1,body.get("total").asInt());assertEquals(1,body.get("items").size());
        assertEquals(0,body.get("page").asInt());assertEquals(1,body.get("pageSize").asInt());
        assertEquals(1,body.get("totalPages").asInt());assertFalse(body.get("hasNext").asBoolean());
        var defaults=json.readTree(get("/found-items").body());assertEquals(20,defaults.get("pageSize").asInt());
        assertFalse(response.body().contains("password"));assertFalse(response.body().contains("claimantEmail"));
    }
    @Test void rejectsInvalidParametersWith400() throws Exception {
        for(String params:new String[]{"page=-1","page=10001","pageSize=0","pageSize=101","page=abc","sort=SQL","category=BAD","custody=BAD","status=ACTIVE","dateFrom=bad","dateFrom=2026-10-03&dateTo=2026-10-01","q="+"a".repeat(201),"location="+"a".repeat(201)}) {
            var response=get("/found-items?"+params);assertEquals(400,response.statusCode(),params+" "+response.body());
            assertEquals(400,json.readTree(response.body()).get("status").asInt());
        }
        assertEquals(400,get("/lost-reports?custody=OFFICE").statusCode());
        assertEquals(400,get("/lost-reports?status=RETURNED").statusCode());
    }
    @Test void emptyAndOutOfRangePagesKeepValidMetadata() throws Exception {
        var beyond=json.readTree(get("/found-items?page=2&pageSize=1").body());
        assertEquals(1,beyond.get("total").asInt());assertEquals(0,beyond.get("items").size());
        var empty=json.readTree(get("/lost-reports?status=ACTIVE").body());
        assertEquals(0,empty.get("total").asInt());assertEquals(0,empty.get("totalPages").asInt());
    }
    @Test void catalogsStillRequireSession() throws Exception {
        HttpClient anonymous=HttpClient.newHttpClient();
        for(String endpoint:new String[]{"/found-items","/lost-reports"})
            assertEquals(401,anonymous.send(HttpRequest.newBuilder(URI.create(base()+endpoint)).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
