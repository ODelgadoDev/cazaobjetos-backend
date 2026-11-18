package mx.utch.cazaobjetos.service;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.exception.BusinessException;
import mx.utch.cazaobjetos.repository.ClaimActivityRepository;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceTest {
    @Autowired ItemService items;
    @Autowired ClaimDossierService dossier;
    @Autowired ClaimQueryService queries;
    @Autowired NotificationService notifications;
    @Autowired NotificationEventService emitter;
    @Autowired ClaimActivityRepository activities;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate db;
    UUID finder,owner,other,teacher,found,claim;
    @BeforeEach void setup() {
        db.execute("TRUNCATE app_user CASCADE");finder=user("finder","STUDENT");owner=user("owner","STUDENT");other=user("other","STUDENT");teacher=user("teacher","TEACHER");
        found=items.createFound(finder,report("Objeto"));claim=items.claim(owner,found,new ClaimRequestDto("SECRETO que no debe ir en un aviso",null));
    }
    UUID user(String name,String role) {var id=UUID.randomUUID();db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES(?,?,?,?,?,true)",id,name+"@utch.edu.mx","unused",name,role);return id;}
    ReportRequestDto report(String title) {return new ReportRequestDto(title,"Ficha pública",Category.OTHER,"I",LocalDate.now());}
    List<NotificationResponseDto> inbox(UUID actor) {return notifications.list(actor,NotificationFilter.ALL,0,100).items();}
    List<NotificationResponseDto> kind(UUID actor,NotificationKind kind) {return inbox(actor).stream().filter(n->n.kind()==kind).toList();}
    void fails(int status,Runnable f) {assertEquals(status,assertThrows(BusinessException.class,f::run).getStatusCode().value());}
    @Test void publicationReceiptsAndNewClaimsReachOnlyTheirParticipants() {
        assertEquals(new NotificationSummaryDto(2,2),notifications.summary(finder));assertEquals(new NotificationSummaryDto(1,1),notifications.summary(owner));assertEquals(0,notifications.summary(other).total());
        assertEquals(found,kind(finder,NotificationKind.FOUND_PUBLISHED).getFirst().targetId());
        var sent=kind(owner,NotificationKind.SUBMITTED).getFirst();assertEquals(claim,sent.targetId());assertEquals(NotificationTarget.CLAIM,sent.targetType());
        assertTrue(inbox(finder).stream().noneMatch(n->n.body().contains("SECRETO")));
        var loss=items.createLost(owner,report("Extraviado"));assertEquals(loss,kind(owner,NotificationKind.LOSS_PUBLISHED).getFirst().targetId());
    }
    @Test void onlyExplicitClickReadsOneNoticeAndReadingIsIdempotent() {
        var notice=kind(finder,NotificationKind.SUBMITTED).getFirst();notifications.list(finder,NotificationFilter.UNREAD,0,20);queries.detail(finder,claim);dossier.activity(finder,claim,0,20);
        assertEquals(2,notifications.summary(finder).unread());assertNull(kind(finder,NotificationKind.SUBMITTED).getFirst().readAt());
        var read=notifications.read(finder,notice.id());assertNotNull(read.readAt());assertEquals(read.readAt(),notifications.read(finder,notice.id()).readAt());
        assertEquals(1,notifications.summary(finder).unread());assertEquals(2,notifications.summary(finder).total());fails(404,()->notifications.read(owner,notice.id()));
    }
    @Test void clearingArchivesOnlyReadNoticesAndNeverDeletesHistory() {
        var notice=kind(finder,NotificationKind.FOUND_PUBLISHED).getFirst();notifications.read(finder,notice.id());
        long history=dossier.activity(owner,claim,0,100).total();assertEquals(1,notifications.clearRead(finder).cleared());assertEquals(0,notifications.clearRead(finder).cleared());
        assertEquals(new NotificationSummaryDto(1,1),notifications.summary(finder));assertEquals(1,notifications.summary(owner).total());
        assertEquals(3L,db.queryForObject("SELECT count(*) FROM user_notification",Long.class));assertEquals(history,dossier.activity(owner,claim,0,100).total());fails(404,()->notifications.read(finder,notice.id()));
    }
    @Test void commentsAndEvidenceNotifyTheOtherParticipantOnceWithoutPrivateContent() throws Exception {
        dossier.comment(owner,claim,new ClaimCommentRequestDto("SECRETO adicional"),"comment-notify-01");dossier.comment(owner,claim,new ClaimCommentRequestDto("SECRETO adicional"),"comment-notify-01");
        assertEquals(1,kind(finder,NotificationKind.COMMENT).size());assertTrue(kind(owner,NotificationKind.COMMENT).isEmpty());assertFalse(kind(finder,NotificationKind.COMMENT).getFirst().body().contains("SECRETO"));
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(5,5,BufferedImage.TYPE_INT_RGB),"png",out);var file=new MockMultipartFile("file","private-name.png","image/png",out.toByteArray());
        dossier.upload(finder,claim,file,"evidence-notify-01");dossier.upload(finder,claim,file,"evidence-notify-01");assertEquals(1,kind(owner,NotificationKind.EVIDENCE_ADDED).size());assertTrue(kind(finder,NotificationKind.EVIDENCE_ADDED).isEmpty());assertEquals(0,notifications.summary(other).total());
    }
    @Test void decisionsAndAutomaticClosureUseTheRightRecipients() {
        UUID second=items.claim(other,found,new ClaimRequestDto("OTRO SECRETO",null));items.decide(finder,claim,"accept");assertEquals(1,kind(owner,NotificationKind.ACCEPTED).size());assertTrue(kind(other,NotificationKind.ACCEPTED).isEmpty());
        items.decide(owner,claim,"complete");items.decide(owner,claim,"complete");assertEquals(1,kind(owner,NotificationKind.COMPLETED).size());assertEquals(1,kind(finder,NotificationKind.COMPLETED).size());assertEquals(second,kind(other,NotificationKind.CLOSED).getFirst().targetId());assertTrue(kind(other,NotificationKind.COMPLETED).isEmpty());
        UUID another=items.createFound(finder,report("Otro")),rejected=items.claim(owner,another,new ClaimRequestDto("Descripción privada",null));items.decide(finder,rejected,"reject","Motivo privado");assertEquals(1,kind(owner,NotificationKind.REJECTED).size());assertFalse(kind(owner,NotificationKind.REJECTED).getFirst().body().contains("Motivo privado"));
        UUID cancelled=items.claim(owner,another,new ClaimRequestDto("Otra solicitud",null));items.decide(owner,cancelled,"cancel");assertEquals(cancelled,kind(finder,NotificationKind.CANCELLED).getFirst().targetId());
    }
    @Test void manualCustodyNotifiesClaimantWithoutAssigningAnAccountToTeacher() {
        items.custody(finder,found,new CustodyRequestDto(CustodyStatus.WITH_TEACHER,"teacher","I-15"));items.custody(finder,found,new CustodyRequestDto(CustodyStatus.WITH_TEACHER,"teacher","I-15"));
        assertEquals(1,kind(owner,NotificationKind.CUSTODY_CHANGED).size());assertEquals(0,notifications.summary(teacher).total());assertEquals(finder,items.found(found).responsibleId());
        items.editFound(finder,found,report("Corregido"));assertEquals(1,kind(owner,NotificationKind.PUBLICATION_EDITED).size());
        items.custody(finder,found,new CustodyRequestDto(CustodyStatus.OFFICE,null,null));fails(409,()->items.custody(finder,found,new CustodyRequestDto(CustodyStatus.WITH_FINDER,null,null)));assertEquals(2,kind(owner,NotificationKind.CUSTODY_CHANGED).size());
    }
    @Test void legacyTransferRemovesOldAccessFromInboxCountsAndReadEndpoint() {
        var old=kind(finder,NotificationKind.SUBMITTED).getFirst();items.delegate(finder,found,teacher);
        assertTrue(inbox(finder).stream().noneMatch(n->n.targetType()==NotificationTarget.CLAIM));assertEquals(new NotificationSummaryDto(1,1),notifications.summary(finder));fails(404,()->notifications.read(finder,old.id()));
        assertEquals(1,kind(teacher,NotificationKind.DELEGATED).size());assertEquals(1,kind(owner,NotificationKind.DELEGATED).size());assertEquals(0,notifications.clearRead(other).cleared());
    }
    @Test void replayingAnOriginCannotDuplicateOrResurrectClearedNotifications() {
        var entry=activities.findAll().stream().filter(e->e.getClaimId().equals(claim)).findFirst().orElseThrow();
        var notice=kind(owner,NotificationKind.SUBMITTED).getFirst();notifications.read(owner,notice.id());notifications.clearRead(owner);
        long count=db.queryForObject("SELECT count(*) FROM user_notification",Long.class);
        new TransactionTemplate(transactions).executeWithoutResult(status->{emitter.activity(entry);emitter.activity(entry);});
        assertEquals(count,db.queryForObject("SELECT count(*) FROM user_notification",Long.class));assertEquals(0,notifications.summary(owner).total());
    }
    @Test void originAndNoticesRollBackTogether() {
        long foundCount=db.queryForObject("SELECT count(*) FROM found_item",Long.class),noticeCount=db.queryForObject("SELECT count(*) FROM user_notification",Long.class);
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).executeWithoutResult(status->{items.createFound(finder,report("Se revierte"));dossier.comment(owner,claim,new ClaimCommentRequestDto("Se revierte también"),null);throw new IllegalStateException("rollback");}));
        assertEquals(foundCount,db.queryForObject("SELECT count(*) FROM found_item",Long.class));assertEquals(noticeCount,db.queryForObject("SELECT count(*) FROM user_notification",Long.class));assertTrue(kind(finder,NotificationKind.COMMENT).isEmpty());
    }
    @Test void concurrentCleanupCannotConsumeAnIncomingUnreadComment() throws Exception {
        notifications.read(finder,kind(finder,NotificationKind.FOUND_PUBLISHED).getFirst().id());var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var clear=pool.submit(()->{gate.await();return notifications.clearRead(finder);});var incoming=pool.submit(()->{gate.await();return dossier.comment(owner,claim,new ClaimCommentRequestDto("Nuevo durante limpieza"),"concurrent-notify-1");});gate.countDown();clear.get(10,TimeUnit.SECONDS);incoming.get(10,TimeUnit.SECONDS);
        }
        assertEquals(2,notifications.summary(finder).unread());assertNull(kind(finder,NotificationKind.COMMENT).getFirst().readAt());
    }
    @Test void unreadFilterAndPaginationStayScopedAndStable() {
        for(int i=0;i<24;i++)dossier.comment(owner,claim,new ClaimCommentRequestDto("Comentario "+i),null);
        var first=notifications.list(finder,NotificationFilter.ALL,0,10);var second=notifications.list(finder,NotificationFilter.ALL,1,10);assertEquals(26,first.total());assertTrue(first.hasNext());assertTrue(first.items().stream().noneMatch(a->second.items().stream().anyMatch(b->a.id().equals(b.id()))));
        notifications.read(finder,first.items().getFirst().id());assertEquals(25,notifications.list(finder,NotificationFilter.UNREAD,0,100).total());assertEquals(1,notifications.list(owner,NotificationFilter.ALL,0,100).total());
        fails(400,()->notifications.list(finder,NotificationFilter.ALL,-1,20));fails(400,()->notifications.list(finder,NotificationFilter.ALL,0,101));
    }
}
