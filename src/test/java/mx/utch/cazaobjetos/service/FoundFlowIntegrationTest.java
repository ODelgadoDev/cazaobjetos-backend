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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.exception.BusinessException;
import mx.utch.cazaobjetos.repository.ClaimActivityRepository;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties="app.frontend-url=http://127.0.0.1:5177")
@ActiveProfiles("test")
class FoundFlowIntegrationTest {
    @Autowired ItemService items;
    @Autowired CreationService creation;
    @Autowired FoundPublicationService publications;
    @Autowired NotificationService notifications;
    @Autowired ClaimDossierService dossier;
    @Autowired MilestoneEmailService mail;
    @Autowired ClaimActivityRepository activities;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate db;
    UUID finder,owner,other,teacher,loss;
    @BeforeEach void setup() {
        db.execute("TRUNCATE app_user,email_outbox CASCADE");finder=user("finder","STUDENT");owner=user("owner","STUDENT");other=user("other","STUDENT");teacher=user("teacher","TEACHER");loss=items.createLost(owner,report("Mochila extraviada"));
    }
    UUID user(String name,String role) {var id=UUID.randomUUID();db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES(?,?,?,?,?,true)",id,name+"@utch.edu.mx","unused",name,role);return id;}
    ReportRequestDto report(String title) {return new ReportRequestDto(title,"Descripción pública",Category.CLOTHING,"I-15",LocalDate.now());}
    FoundPublicationRequestDto request(UUID source) {return new FoundPublicationRequestDto(report("Mochila encontrada"),source,new CustodyRequestDto(CustodyStatus.WITH_TEACHER,"Docente de palabra","I-12"));}
    MockMultipartFile image(int side) {try {var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(side,side,BufferedImage.TYPE_INT_RGB),"png",out);return new MockMultipartFile("file","photo.png","image/png",out.toByteArray());}catch(IOException e){throw new RuntimeException(e);}}
    UUID publish() {return creation.publishFound(finder,"flow-publish-01",request(loss),image(8));}
    long count(String table) {return db.queryForObject("SELECT count(*) FROM "+table,Long.class);}
    long mails(String event) {return db.queryForObject("SELECT count(*) FROM email_outbox WHERE event_key LIKE ?",Long.class,"MILESTONE:"+event+":%");}
    long matches() {return notifications.list(owner,NotificationFilter.ALL,0,100).items().stream().filter(n->n.kind()==NotificationKind.POSSIBLE_MATCH).count();}
    void fails(int status,Runnable action){assertEquals(status,assertThrows(BusinessException.class,action::run).getStatusCode().value());}
    @Test void fullPublicationCommitsPhotoCustodySourceAndOnePossibleMatchWithoutCreatingClaim() {
        UUID found=publish();var item=items.found(found);assertEquals(loss,item.sourceLostReportId());assertEquals(finder,item.responsibleId());assertEquals(CustodyStatus.WITH_TEACHER,item.custody());assertEquals("Docente de palabra",item.teacherName());
        assertEquals(1,count("publication_photo"));assertEquals(1,matches());assertEquals(0,count("claim"));assertEquals(LostStatus.ACTIVE,items.lost(loss).status());assertEquals(0,notifications.summary(teacher).total());
        var notice=notifications.list(owner,NotificationFilter.ALL,0,100).items().stream().filter(n->n.kind()==NotificationKind.POSSIBLE_MATCH).findFirst().orElseThrow();assertEquals(NotificationTarget.FOUND_ITEM,notice.targetType());assertEquals(found,notice.targetId());assertNull(notice.readAt());assertTrue(notice.body().contains("decide"));
    }
    @Test void invalidPhotoAndCustodyRollBackPublicationMailAuditAndNotification() {
        long mail=count("email_outbox"),audit=count("audit_event"),notice=count("user_notification");
        fails(415,()->creation.publishFound(finder,"bad-publish-01",request(loss),new MockMultipartFile("file","x.png","image/png","fake".getBytes())));
        var bad=new FoundPublicationRequestDto(report("Otro"),loss,new CustodyRequestDto(CustodyStatus.WITH_TEACHER," ",null));fails(400,()->creation.publishFound(finder,"bad-publish-02",bad,image(8)));
        assertEquals(0,count("found_item"));assertEquals(0,count("publication_photo"));assertEquals(0,count("creation_request"));assertEquals(mail,count("email_outbox"));assertEquals(audit,count("audit_event"));assertEquals(notice,count("user_notification"));
    }
    @Test void retriesAfterSourceClosureReturnOriginalWithoutDuplicates() {
        UUID found=publish();items.recoverLost(owner,loss);long notices=count("user_notification"),mails=count("email_outbox");
        assertEquals(found,creation.publishFound(finder,"flow-publish-01",request(loss),image(8)));assertEquals(1,count("found_item"));assertEquals(1,count("publication_photo"));assertEquals(1,matches());assertEquals(notices,count("user_notification"));assertEquals(mails,count("email_outbox"));
        fails(409,()->creation.publishFound(finder,"flow-publish-01",request(loss),image(9)));
    }
    @Test void closedWithdrawnMissingAndOwnSourcesNeverPublishOrNotify() {
        fails(409,()->creation.publishFound(owner,"own-source-01",request(loss),image(8)));items.recoverLost(owner,loss);fails(409,()->creation.publishFound(finder,"closed-source-01",request(loss),image(8)));
        UUID withdrawn=items.createLost(owner,report("Otro"));items.withdrawLost(owner,withdrawn);fails(404,()->creation.publishFound(finder,"withdrawn-source-01",request(withdrawn),image(8)));fails(404,()->creation.publishFound(finder,"missing-source-01",request(UUID.randomUUID()),image(8)));
        assertEquals(0,count("found_item"));assertEquals(0,matches());assertEquals(0,mails("FOUND_PUBLISHED"));
    }
    @Test void independentPublicationHasNoMatchAndMetadataEditsKeepSource() {
        UUID independent=creation.publishFound(finder,"independent-01",request(null),image(8));assertNull(items.found(independent).sourceLostReportId());assertEquals(0,matches());
        UUID found=publish();items.editFound(finder,found,report("Corregida"));items.custody(finder,found,new CustodyRequestDto(CustodyStatus.OFFICE,null,null));assertEquals(loss,items.found(found).sourceLostReportId());
    }
    @Test void ownerCanFilterPersistedMatchesWithoutSeeingWithdrawnOrOtherReports() {
        UUID found=publish();UUID unrelated=items.createFound(other,report("No vinculado"));var page=publications.matches(owner,loss,0,1);assertEquals(1,page.total());assertEquals(found,page.items().getFirst().id());assertNotEquals(unrelated,page.items().getFirst().id());fails(404,()->publications.matches(finder,loss,0,10));fails(400,()->publications.matches(owner,loss,-1,10));fails(400,()->publications.matches(owner,loss,0,101));
        items.withdrawFound(finder,found);assertEquals(0,publications.matches(owner,loss,0,10).total());assertEquals(1,matches());
    }
    @Test void linkedClaimCompletesOnlyAfterAcceptanceAndClosesSourceWithReadOnlyEvidence() {
        UUID found=publish(),claim=creation.claimWithEvidence(owner,found,"claim-flow-01",new ClaimRequestDto("SECRETO de propiedad",loss),List.of(image(6)));
        fails(409,()->items.decide(owner,claim,"complete"));dossier.comment(owner,claim,new ClaimCommentRequestDto("SECRETO en comentario"),"flow-comment-01");items.decide(finder,claim,"accept");items.custody(finder,found,new CustodyRequestDto(CustodyStatus.OFFICE,null,"Mesa del I"));items.decide(owner,claim,"complete");
        assertEquals(FoundStatus.RETURNED,items.found(found).status());assertEquals(LostStatus.RECOVERED,items.lost(loss).status());assertEquals(1,dossier.evidence(owner,claim).size());fails(409,()->dossier.comment(owner,claim,new ClaimCommentRequestDto("Tarde"),null));assertEquals(1,publications.matches(owner,loss,0,10).total());
    }
    @Test void emailsCoverOnlyPublicationStartAndEndAndNeverIncludePrivateProof() {
        UUID found=publish(),claim=items.claim(owner,found,new ClaimRequestDto("SECRETO original",loss));assertEquals(1,mails("LOSS_PUBLISHED"));assertEquals(1,mails("FOUND_PUBLISHED"));assertEquals(2,mails("SUBMITTED"));
        long before=count("email_outbox");dossier.comment(owner,claim,new ClaimCommentRequestDto("SECRETO adicional"),null);dossier.upload(owner,claim,image(5),null);items.decide(finder,claim,"accept");items.custody(finder,found,new CustodyRequestDto(CustodyStatus.OFFICE,null,null));items.editFound(finder,found,report("Texto corregido"));assertEquals(before,count("email_outbox"));
        items.decide(owner,claim,"complete");items.decide(owner,claim,"complete");assertEquals(2,mails("COMPLETED"));
        var bodies=db.queryForList("SELECT body FROM email_outbox",String.class);assertTrue(bodies.stream().noneMatch(b->b.contains("SECRETO")));assertTrue(bodies.stream().allMatch(b->b.contains("http://127.0.0.1:5177/")));
        assertEquals(count("email_outbox"),db.queryForObject("SELECT count(DISTINCT event_key) FROM email_outbox",Long.class));
    }
    @Test void rejectedCancelledAndOtherClosedClaimsReceiveOneEndMilestoneEach() {
        UUID found=publish(),rejected=items.claim(owner,found,new ClaimRequestDto("Prueba privada",null));items.decide(finder,rejected,"reject","SECRETO en el motivo");assertEquals(2,mails("REJECTED"));
        UUID cancelled=items.claim(owner,found,new ClaimRequestDto("Otra solicitud",null));items.decide(owner,cancelled,"cancel");assertEquals(2,mails("CANCELLED"));
        UUID winner=items.claim(owner,found,new ClaimRequestDto("Prueba final",loss)),closed=items.claim(other,found,new ClaimRequestDto("Prueba ajena",null));items.decide(finder,winner,"accept");items.decide(owner,winner,"complete");assertEquals(1,mails("CLOSED"));
        String body=db.queryForObject("SELECT body FROM email_outbox WHERE event_key LIKE 'MILESTONE:CLOSED:%'",String.class);assertFalse(body.contains(winner.toString()));assertTrue(body.contains(closed.toString()));assertFalse(body.contains("SECRETO"));
    }
    @Test void originalFinderReceivesOnlyPublicContextAfterLegacyDelegation() {
        UUID found=publish(),claim=items.claim(owner,found,new ClaimRequestDto("SECRETO de propiedad",loss));items.delegate(finder,found,teacher);items.decide(teacher,claim,"accept");items.decide(owner,claim,"complete");
        assertEquals(3,mails("COMPLETED"));String body=db.queryForObject("SELECT body FROM email_outbox WHERE recipient='finder@utch.edu.mx' AND event_key LIKE 'MILESTONE:COMPLETED:%'",String.class);
        assertTrue(body.contains("/reclamo/"+found));assertFalse(body.contains(claim.toString()));assertFalse(body.contains("SECRETO"));
    }
    @Test void replayOfMilestoneOriginDoesNotDuplicateMessages() {
        UUID found=publish(),claim=items.claim(owner,found,new ClaimRequestDto("Prueba",null));long before=count("email_outbox");var origin=activities.findAll().stream().filter(e->e.getClaimId().equals(claim)&&e.getKind()==ClaimActivityKind.SUBMITTED).findFirst().orElseThrow();
        new TransactionTemplate(transactions).executeWithoutResult(s->{mail.activity(origin);mail.activity(origin);});assertEquals(before,count("email_outbox"));
    }
    @Test void concurrentPublicationRetriesCommitOneCompleteRecord() throws Exception {
        var gate=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)) {Callable<UUID> f=()->{gate.await();return publish();};var a=pool.submit(f);var b=pool.submit(f);gate.countDown();assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));}
        assertEquals(1,count("found_item"));assertEquals(1,count("publication_photo"));assertEquals(1,matches());assertEquals(1,mails("FOUND_PUBLISHED"));
    }
    @Test void closureRacingPublicationCannotCreateAMatchFromAnAlreadyClosedSource() throws Exception {
        var gate=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)) {
            var publish=pool.submit(()->{gate.await();try {publish();return 201;}catch(BusinessException e){return e.getStatusCode().value();}});
            var close=pool.submit(()->{gate.await();items.recoverLost(owner,loss);return 204;});gate.countDown();int status=publish.get(10,TimeUnit.SECONDS);assertEquals(204,close.get(10,TimeUnit.SECONDS));assertTrue(status==201||status==409);assertEquals(status==201?1:0,count("found_item"));assertEquals(status==201?1:0,matches());assertEquals(status==201?1:0,count("publication_photo"));
        }
    }
}
