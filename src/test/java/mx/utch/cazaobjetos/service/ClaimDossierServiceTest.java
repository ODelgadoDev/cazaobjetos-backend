package mx.utch.cazaobjetos.service;

import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ClaimDossierServiceTest {
    @Autowired ItemService items;
    @Autowired ClaimDossierService dossier;
    @Autowired ClaimQueryService queries;
    @Autowired CreationService creations;
    @Autowired JdbcTemplate db;
    @Autowired ProfilePhotoService profilePhotos;
    UUID finder,owner,other,teacher,found,claim;
    @BeforeEach void setup() {
        db.execute("TRUNCATE app_user CASCADE");
        finder=user("finder","STUDENT");owner=user("owner","STUDENT");other=user("other","STUDENT");teacher=user("teacher","TEACHER");
        found=items.createFound(finder,report("Objeto"));claim=items.claim(owner,found,new ClaimRequestDto("Detalle privado del reclamante",null));
    }
    UUID user(String name,String role) {var id=UUID.randomUUID();db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES (?,?,?,?,?,true)",id,name+"@utch.edu.mx","unused",name,role);return id;}
    ReportRequestDto report(String title) {return new ReportRequestDto(title,"Descripción pública",Category.OTHER,"Edificio I",LocalDate.now());}
    void fails(int status,Runnable action) {assertEquals(status,assertThrows(BusinessException.class,action::run).getStatusCode().value());}
    MockMultipartFile png(int width,int height) throws Exception {var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"png",out);return new MockMultipartFile("file","private-name.png","image/png",out.toByteArray());}
    ClaimCommentRequestDto comment(String body) {return new ClaimCommentRequestDto(body);}

    @Test void conversationAvatarsAreCurrentAndScopedToItsAuthors() throws Exception {
        var comment=dossier.comment(owner,claim,comment("Puedo pasar mañana."),null);
        profilePhotos.replace("owner@utch.edu.mx",png(8,8));
        db.update("UPDATE app_user SET degree='Mecatrónica' WHERE id=?",owner);
        var entry=dossier.activity(finder,claim,0,20).items().stream().filter(e->e.id().equals(comment.id())).findFirst().orElseThrow();
        assertNotNull(entry.actorAvatarVersion());
        assertEquals("Mecatrónica",entry.actorDegree());
        assertTrue(dossier.avatar(finder,claim,owner).getContent().length>0);
        fails(404,()->dossier.avatar(other,claim,owner));
        profilePhotos.replace("other@utch.edu.mx",png(8,8));
        fails(404,()->dossier.avatar(finder,claim,other));
        profilePhotos.delete("owner@utch.edu.mx");
        assertNull(dossier.activity(finder,claim,0,20).items().stream().filter(e->e.id().equals(comment.id())).findFirst().orElseThrow().actorAvatarVersion());
    }
    @Test void privateCommentsPreserveAuthorSnapshotAndIdempotency() {
        var first=dossier.comment(owner,claim,comment("  Tengo una foto anterior.  "),"comment-001");
        assertEquals("Tengo una foto anterior.",first.body());
        assertEquals(first.id(),dossier.comment(owner,claim,comment("Tengo una foto anterior."),"comment-001").id());
        fails(409,()->dossier.comment(owner,claim,comment("Otro contenido"),"comment-001"));
        dossier.comment(finder,claim,comment("Puedes adjuntarla aquí."),"comment-001");
        db.update("UPDATE app_user SET display_name='Nombre cambiado' WHERE id=?",owner);
        var activity=dossier.activity(owner,claim,0,20);
        assertEquals(3,activity.total());
        assertEquals("owner",activity.items().stream().filter(e->e.id().equals(first.id())).findFirst().orElseThrow().actorName());
        fails(404,()->dossier.activity(other,claim,0,20));fails(404,()->dossier.comment(teacher,claim,comment("No autorizado"),null));
        fails(400,()->dossier.comment(owner,claim,comment("  "),null));fails(400,()->dossier.comment(owner,claim,comment("x".repeat(2001)),null));
        fails(400,()->dossier.comment(owner,claim,comment("Mal\u0000texto"),null));
    }
    @Test void commentsRemainWritableThroughAcceptanceAndReadOnlyAfterEveryClosure() {
        assertTrue(queries.detail(owner,claim).canContribute());items.decide(finder,claim,"accept");
        dossier.comment(owner,claim,comment("Paso en la tarde."),null);assertTrue(queries.detail(owner,claim).canContribute());
        items.decide(owner,claim,"complete");
        assertFalse(queries.detail(owner,claim).canContribute());fails(409,()->dossier.comment(owner,claim,comment("Fuera de plazo"),null));
        assertEquals(4,dossier.activity(finder,claim,0,20).total());
        for(String action:List.of("reject","cancel")) {
            UUID item=items.createFound(finder,report(action));UUID c=items.claim(owner,item,new ClaimRequestDto("Descripción de prueba",null));
            items.decide(action.equals("reject")?finder:owner,c,action);
            fails(409,()->dossier.comment(owner,c,comment("Fuera de plazo"),null));assertEquals(2,dossier.activity(owner,c,0,20).total());
        }
    }
    @Test void closingOtherClaimsUsesSystemActorAndRetainsTheirPrivateHistory() {
        UUID second=items.claim(other,found,new ClaimRequestDto("Secreto de otra persona",null));
        dossier.comment(other,second,comment("Información propia"),null);
        items.decide(finder,claim,"accept");items.decide(owner,claim,"complete");
        var timeline=dossier.activity(other,second,0,20);
        var close=timeline.items().stream().filter(e->e.kind()==ClaimActivityKind.CLOSED).findFirst().orElseThrow();
        assertNull(close.actorId());assertEquals("Sistema",close.actorName());
        assertFalse(timeline.items().stream().anyMatch(e->owner.equals(e.actorId())));
        fails(409,()->dossier.comment(other,second,comment("Cerrado"),null));fails(404,()->dossier.activity(owner,second,0,20));
    }
    @Test void evidenceIsPrivateValidatedImmutableAndLimited() throws Exception {
        var image=png(8,6);var saved=dossier.upload(owner,claim,image,"evidence-001");
        assertEquals(saved.id(),dossier.upload(owner,claim,image,"evidence-001").id());
        var bytes=dossier.content(finder,claim,saved.id()).getContent();assertNotNull(ImageIO.read(new ByteArrayInputStream(bytes)));
        fails(404,()->dossier.content(other,claim,saved.id()));
        UUID different=items.claim(other,found,new ClaimRequestDto("Descripción privada ajena",null));
        fails(404,()->dossier.content(other,different,saved.id()));
        fails(415,()->dossier.upload(owner,claim,new MockMultipartFile("file","x.png","image/png","<html>fake</html>".getBytes()),null));
        fails(400,()->dossier.upload(owner,claim,new MockMultipartFile("file",new byte[0]),null));
        fails(413,()->dossier.upload(owner,claim,new MockMultipartFile("file",new byte[5*1024*1024+1]),null));
        fails(409,()->dossier.upload(owner,claim,uncheckedPng(9,7),"evidence-001"));
        for(int i=0;i<4;i++) dossier.upload(owner,claim,image,null);
        assertEquals(5,dossier.evidence(owner,claim).size());fails(409,()->dossier.upload(owner,claim,image,null));
        items.decide(finder,claim,"reject");fails(409,()->dossier.upload(owner,claim,image,null));
        assertEquals(5,dossier.evidence(owner,claim).size());assertNotNull(dossier.content(owner,claim,saved.id()));
    }
    MockMultipartFile uncheckedPng(int w,int h) {try{return png(w,h);}catch(Exception error){throw new RuntimeException(error);}}
    @Test void multipartCreationAndRetriesAreAtomic() throws Exception {
        UUID item=items.createFound(finder,report("Otro"));var request=new ClaimRequestDto("Prueba de creación atómica",null);
        var good=png(5,5);var bad=new MockMultipartFile("file","x.png","image/png","fake".getBytes());
        long before=db.queryForObject("SELECT count(*) FROM claim",Long.class);
        fails(415,()->creations.claimWithEvidence(owner,item,"atomic-001",request,List.of(good,bad)));
        assertEquals(before,db.queryForObject("SELECT count(*) FROM claim",Long.class));assertEquals(0L,db.queryForObject("SELECT count(*) FROM creation_request",Long.class));
        UUID created=creations.claimWithEvidence(owner,item,"atomic-001",request,List.of(good));
        assertEquals(created,creations.claimWithEvidence(owner,item,"atomic-001",request,List.of(good)));
        assertEquals(1,dossier.evidence(owner,created).size());assertEquals(2,dossier.activity(owner,created,0,20).total());
        fails(409,()->creations.claimWithEvidence(owner,item,"atomic-001",request,List.of(uncheckedPng(7,7))));
        items.decide(finder,created,"accept");items.decide(owner,created,"complete");
        assertEquals(created,creations.claimWithEvidence(owner,item,"atomic-001",request,List.of(good)));
    }
    @Test void manualTeacherAndOfficeCustodyKeepResponsibilityAndAreRecorded() {
        items.decide(finder,claim,"accept");
        items.custody(finder,found,new CustodyRequestDto(CustodyStatus.WITH_TEACHER,"Alfonso","Salón I-12"));
        var item=items.found(found);assertEquals(finder,item.responsibleId());assertEquals("Alfonso",item.teacherName());assertNotNull(item.custodyMarkedAt());
        fails(403,()->items.custody(other,found,new CustodyRequestDto(CustodyStatus.OFFICE,null,null)));
        fails(400,()->items.custody(finder,found,new CustodyRequestDto(CustodyStatus.WITH_TEACHER," ",null)));
        items.custody(finder,found,new CustodyRequestDto(CustodyStatus.OFFICE,null,"Mesa de objetos perdidos"));
        fails(409,()->items.custody(finder,found,new CustodyRequestDto(CustodyStatus.WITH_FINDER,null,null)));
        assertEquals(ClaimStatus.ACCEPTED,queries.detail(owner,claim).claim().status());
        assertEquals(2,dossier.activity(owner,claim,0,20).items().stream().filter(e->e.kind()==ClaimActivityKind.CUSTODY_CHANGED).count());
        items.decide(owner,claim,"complete");fails(409,()->items.custody(finder,found,new CustodyRequestDto(CustodyStatus.OFFICE,null,"Otra")));
    }
    @Test void editsDuringClaimHaveSnapshotsButWithdrawalAndCompletedEditsAreBlocked() {
        var first=dossier.activity(owner,claim,0,20).items().getFirst();assertTrue(first.detailsJson().contains("Objeto"));
        items.editFound(finder,found,report("Nombre corregido"));
        var change=dossier.activity(owner,claim,0,20).items().stream().filter(e->e.kind()==ClaimActivityKind.PUBLICATION_EDITED).findFirst().orElseThrow();
        assertTrue(change.detailsJson().contains("Nombre corregido"));assertTrue(change.detailsJson().contains("Objeto"));
        fails(409,()->items.withdrawFound(finder,found));
        items.decide(finder,claim,"accept");items.editFound(finder,found,report("Corrección final"));
        items.decide(owner,claim,"complete");fails(409,()->items.editFound(finder,found,report("Tarde")));
    }
    @Test void legacyDelegationTransfersPrivateAccessWithoutErasingHistory() throws Exception {
        var image=dossier.upload(owner,claim,png(4,4),null);
        items.delegate(finder,found,teacher);
        fails(404,()->dossier.activity(finder,claim,0,20));fails(404,()->dossier.content(finder,claim,image.id()));
        assertEquals(1,dossier.evidence(teacher,claim).size());assertEquals(3,dossier.activity(owner,claim,0,20).total());
        dossier.comment(teacher,claim,comment("Revisaré la información."),null);
    }
    @Test void paginationIsStableAndScoped() {
        for(int i=0;i<24;i++)dossier.comment(owner,claim,comment("Comentario "+i),null);
        var first=dossier.activity(owner,claim,0,10);var second=dossier.activity(owner,claim,1,10);
        assertEquals(25,first.total());assertTrue(first.hasNext());assertTrue(first.items().stream().noneMatch(a->second.items().stream().anyMatch(b->a.id().equals(b.id()))));
        fails(400,()->dossier.activity(owner,claim,-1,20));fails(400,()->dossier.activity(owner,claim,0,101));
    }
    @Test void simultaneousRetriesCreateOneComment() throws Exception {
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<UUID> write=()->{gate.await();return dossier.comment(owner,claim,comment("Mismo envío"),"parallel-001").id();};
            var a=pool.submit(write);var b=pool.submit(write);gate.countDown();assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM claim_activity WHERE kind='COMMENT'",Long.class));
    }
    @Test void concurrentCompletionAndEvidenceCannotWriteAfterClosure() throws Exception {
        items.decide(finder,claim,"accept");var image=png(5,5);var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var upload=pool.submit(()->{gate.await();try {dossier.upload(owner,claim,image,null);return 201;}catch(BusinessException error){return error.getStatusCode().value();}});
            var complete=pool.submit(()->{gate.await();items.decide(owner,claim,"complete");return 204;});gate.countDown();
            int status=upload.get(10,TimeUnit.SECONDS);assertTrue(status==201||status==409);assertEquals(204,complete.get(10,TimeUnit.SECONDS));
            assertEquals(status==201?1:0,dossier.evidence(owner,claim).size());
            var newest=dossier.activity(owner,claim,0,20).items().getFirst();assertEquals(ClaimActivityKind.COMPLETED,newest.kind());
        }
    }
}
