package mx.utch.cazaobjetos.service;
import java.util.*;
import java.time.LocalDate;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@ActiveProfiles("test")
class PhotoServiceTest {
    @Autowired PhotoService photos;
    @Autowired ItemService items;
    @Autowired JdbcTemplate db;
    UUID owner,other,teacher,found,lost;
    @BeforeEach void setup() {
        db.execute("TRUNCATE app_user CASCADE");owner=user("owner","STUDENT");other=user("other","STUDENT");teacher=user("teacher","TEACHER");
        var request=new ReportRequestDto("Llaves","Llavero azul",Category.KEYS,"I-3",LocalDate.now());
        found=items.createFound(owner,request);lost=items.createLost(owner,request);
    }
    UUID user(String name,String role) {
        UUID id=UUID.randomUUID();db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES (?,?,?,?,?,true)",id,name+"@utch.edu.mx","unused",name,role);return id;
    }
    MockMultipartFile image(String format,int width) throws Exception {
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(width,2,BufferedImage.TYPE_INT_RGB),format,out);
        return new MockMultipartFile("file","../../untrusted.svg","text/plain",out.toByteArray());
    }
    void fails(int status,Runnable action) { assertEquals(status,assertThrows(BusinessException.class,action::run).getStatusCode().value()); }
    @Test void validatesContentAndNormalizesBothFormats() throws Exception {
        for(String format:List.of("png","jpeg")) {
            var f=image(format,2);var p=photos.upload(true,found,owner,f);
            assertEquals("image/"+format,p.contentType());
            var data=photos.content(true,found,p.id(),other);
            assertNotNull(ImageIO.read(new ByteArrayInputStream(data.getContent())));
            assertEquals(data.getContent().length,p.sizeBytes());
        }
        assertEquals(2,photos.list(true,found,other).size());
        fails(415,()->photos.upload(true,found,owner,new MockMultipartFile("file","fake.png","image/png","<script>alert(1)</script>".getBytes())));
        fails(400,()->photos.upload(true,found,owner,new MockMultipartFile("file",new byte[0])));
        fails(413,()->photos.upload(true,found,owner,new MockMultipartFile("file",new byte[5*1024*1024+1])));
        var wide=image("png",6001);fails(400,()->photos.upload(true,found,owner,wide));
    }
    @Test void permissionsScopeAndDeletion() throws Exception {
        var file=image("png",2);var p=photos.upload(true,found,owner,file);
        fails(403,()->photos.upload(true,found,other,file));fails(403,()->photos.delete(true,found,p.id(),other));
        fails(404,()->photos.content(false,lost,p.id(),owner));
        items.delegate(owner,found,teacher);
        fails(403,()->photos.delete(true,found,p.id(),owner));photos.delete(true,found,p.id(),teacher);
        assertTrue(photos.list(true,found,owner).isEmpty());
        fails(404,()->photos.content(true,found,p.id(),owner));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM audit_event WHERE action LIKE 'PHOTO_DELETED_%'",Integer.class));
    }
    @Test void withdrawnPhotosRemainOnlyForTheirParticipants() throws Exception {
        var file=image("jpeg",2);var a=photos.upload(true,found,owner,file);var b=photos.upload(false,lost,owner,file);
        items.withdrawFound(owner,found);items.withdrawLost(owner,lost);
        assertEquals(1,photos.list(true,found,owner).size());assertEquals(1,photos.list(false,lost,owner).size());
        fails(404,()->photos.list(true,found,other));fails(404,()->photos.content(false,lost,b.id(),other));
        fails(409,()->photos.upload(true,found,owner,file));fails(409,()->photos.delete(false,lost,b.id(),owner));
    }
    @Test void activeClaimsAndCompletedItemsBlockPhotoChanges() throws Exception {
        var file=image("png",2);var a=photos.upload(true,found,owner,file);
        var loss=items.createLost(other,new ReportRequestDto("Llaves","Llavero",Category.KEYS,"I",LocalDate.now()));
        var b=photos.upload(false,loss,other,file);
        var claim=items.claim(other,found,new ClaimRequestDto("Llavero azul",loss));
        for(boolean accepted:List.of(false,true)) {
            if(accepted) items.decide(owner,claim,"accept");
            fails(409,()->photos.upload(true,found,owner,file));fails(409,()->photos.delete(true,found,a.id(),owner));
            fails(409,()->photos.upload(false,loss,other,file));fails(409,()->photos.delete(false,loss,b.id(),other));
        }
        items.decide(other,claim,"complete");fails(409,()->photos.delete(true,found,a.id(),owner));
        assertEquals(1,photos.list(true,found,other).size());
    }
    @Test void concurrentUploadsCannotExceedThree() throws Exception {
        var file=image("png",2);photos.upload(true,found,owner,file);photos.upload(true,found,owner,file);
        var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Integer> upload=()->{gate.await();try{photos.upload(true,found,owner,file);return 201;}catch(BusinessException e){return e.getStatusCode().value();}};
            var a=executor.submit(upload);var b=executor.submit(upload);gate.countDown();
            assertEquals(Set.of(201,409),Set.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)));
        }
        assertEquals(3,photos.list(true,found,owner).size());
        var first=photos.list(true,found,owner).getFirst();photos.delete(true,found,first.id(),owner);photos.upload(true,found,owner,file);
    }
}
