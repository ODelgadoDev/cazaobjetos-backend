package mx.utch.cazaobjetos.service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import mx.utch.cazaobjetos.exception.BusinessException;
import mx.utch.cazaobjetos.service.ItemService;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
class ItemServiceTest {
    @Autowired ItemService service;
    @Autowired JdbcTemplate db;
    UUID finder,owner,other,teacher;
    @BeforeEach void setup() {
        db.execute("TRUNCATE email_outbox,audit_event,claim,lost_report,found_item,app_user CASCADE");
        finder=user("finder","STUDENT"); owner=user("owner","STUDENT"); other=user("other","STUDENT"); teacher=user("teacher","TEACHER");
    }
    UUID user(String name,String role) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES (?,?,?,?,?,true)",id,name+"@utch.edu.mx","unused",name,role); return id;
    }
    ReportRequestDto report() { return new ReportRequestDto("Mochila","Mochila negra",Category.OTHER,"Edificio I, salón 3",LocalDate.now()); }
    UUID found() { return service.createFound(finder,report()); }
    UUID claim(UUID actor,UUID item) { return service.claim(actor,item,new ClaimRequestDto("Tiene un llavero dentro",null)); }
    void fails(int status,Runnable action) { assertEquals(status,assertThrows(BusinessException.class,action::run).getStatusCode().value()); }

    @org.springframework.beans.factory.annotation.Autowired CatalogService catalog;
    CatalogQueryDto query(String status) { return new CatalogQueryDto(null,null,null,status,null,null,null,null,0,20); }
    @Test void onlyCurrentResponsibleCanEditAndWithdrawFound() {
        UUID id=found(); service.delegate(finder,id,teacher);
        fails(403,()->service.editFound(finder,id,report()));
        fails(403,()->service.withdrawFound(other,id));
        service.editFound(teacher,id,new ReportRequestDto("Corregida","Descripción corregida",Category.KEYS,"I-2",LocalDate.now()));
        assertEquals("Corregida",service.found(id).title());
        assertEquals(1,catalog.myFoundItems(finder,query(null)).total());
        assertEquals(1,catalog.myFoundItems(teacher,query(null)).total());
        assertEquals(0,catalog.myFoundItems(other,query(null)).total());
        service.withdrawFound(teacher,id);service.withdrawFound(teacher,id);
        assertEquals(0,catalog.found(query(null)).total());
        assertEquals(0,catalog.found(query("WITHDRAWN")).total());
        assertEquals(1,catalog.myFoundItems(finder,query("WITHDRAWN")).total());
        fails(404,()->service.found(id));fails(409,()->claim(owner,id));
        fails(409,()->service.editFound(teacher,id,report()));
        fails(409,()->service.office(teacher,id));
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM audit_event WHERE action='FOUND_WITHDRAWN'",Long.class));
    }
    @Test void activeClaimsProtectFoundAndLinkedLostFromChanges() {
        UUID id=found(),loss=service.createLost(owner,report());
        UUID c=service.claim(owner,id,new ClaimRequestDto("Mi mochila negra",loss));
        for(boolean accepted:List.of(false,true)) {
            if(accepted) service.decide(finder,c,"accept");
            fails(409,()->service.editFound(finder,id,report()));fails(409,()->service.withdrawFound(finder,id));
            fails(409,()->service.editLost(owner,loss,report()));fails(409,()->service.withdrawLost(owner,loss));
        }
        service.decide(owner,c,"cancel");
        service.editFound(finder,id,report());service.editLost(owner,loss,report());
        service.withdrawFound(finder,id);service.withdrawLost(owner,loss);
    }
    @Test void withdrawnLossIsPrivateToMineAndCannotBeRecoveredOrLinked() {
        UUID loss=service.createLost(owner,report());
        fails(403,()->service.editLost(other,loss,report()));fails(403,()->service.withdrawLost(other,loss));
        service.withdrawLost(owner,loss);service.withdrawLost(owner,loss);
        assertEquals(0,catalog.lost(query(null)).total());
        assertEquals(1,catalog.myLostReports(owner,query("WITHDRAWN")).total());
        assertEquals(0,catalog.myLostReports(other,query(null)).total());
        fails(404,()->service.lost(loss));fails(409,()->service.recoverLost(owner,loss));
        UUID id=found();fails(409,()->service.claim(owner,id,new ClaimRequestDto("Detalles",loss)));
    }
    @Test void completedPublicationsCannotBeEditedOrWithdrawn() {
        UUID id=found(),loss=service.createLost(owner,report());
        UUID c=service.claim(owner,id,new ClaimRequestDto("Mi mochila",loss));
        service.decide(finder,c,"accept");service.decide(owner,c,"complete");
        fails(409,()->service.editFound(finder,id,report()));fails(409,()->service.withdrawFound(finder,id));
        fails(409,()->service.editLost(owner,loss,report()));fails(409,()->service.withdrawLost(owner,loss));
    }
    @Test void concurrentWithdrawalAndClaimCannotBothSucceed() throws Exception {
        UUID id=found();var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->{gate.await();try{service.withdrawFound(finder,id);return 204;}catch(BusinessException e){return e.getStatusCode().value();}});
            var b=executor.submit(()->{gate.await();try{claim(owner,id);return 201;}catch(BusinessException e){return e.getStatusCode().value();}});
            gate.countDown();int x=a.get(10,TimeUnit.SECONDS),y=b.get(10,TimeUnit.SECONDS);
            assertTrue((x==204&&y==409)||(x==409&&y==201));
        }
    }
    @Test void officeDoesNotTransferResponsibilityAndRecordsActor() {
        UUID id=found(); service.office(finder,id); service.office(finder,id);
        var item=service.found(id);
        assertEquals(CustodyStatus.OFFICE,item.custody()); assertEquals(finder,item.responsibleId());
        assertEquals(finder,item.officeMarkedBy()); assertNotNull(item.officeMarkedAt());
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM audit_event WHERE action='LEFT_AT_OFFICE'",Long.class));
    }
    @Test void completeRequiresAcceptanceAndCorrectClaimant() {
        UUID item=found(),c=claim(owner,item);
        fails(409,()->service.decide(owner,c,"complete"));
        fails(403,()->service.decide(other,c,"accept"));
        service.decide(finder,c,"accept");
        fails(403,()->service.decide(finder,c,"complete"));
        service.decide(owner,c,"complete"); service.decide(owner,c,"complete");
        assertEquals(FoundStatus.RETURNED,service.found(item).status());
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM email_outbox WHERE recipient='finder@utch.edu.mx' AND status='PENDING'",Long.class));
    }
    @Test void linkedLossClosesAndOtherClaimsClose() {
        UUID item=found(),loss=service.createLost(owner,report());
        UUID c=service.claim(owner,item,new ClaimRequestDto("Descripción de propiedad",loss));
        UUID another=claim(other,item);
        service.decide(finder,c,"accept"); service.decide(owner,c,"complete");
        assertEquals("RECOVERED",db.queryForObject("SELECT status FROM lost_report WHERE id=?",String.class,loss));
        assertEquals("CLOSED",db.queryForObject("SELECT status FROM claim WHERE id=?",String.class,another));
    }
    @Test void concurrentAcceptsHaveExactlyOneWinner() throws Exception {
        UUID item=found(),a=claim(owner,item),b=claim(other,item);
        var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Integer> first=()->{gate.await();try {service.decide(finder,a,"accept");return 204;}catch(BusinessException e){return e.getStatusCode().value();}};
            Callable<Integer> second=()->{gate.await();try {service.decide(finder,b,"accept");return 204;}catch(BusinessException e){return e.getStatusCode().value();}};
            var x=executor.submit(first); var y=executor.submit(second); gate.countDown();
            assertEquals(Set.of(204,409),Set.of(x.get(10,TimeUnit.SECONDS),y.get(10,TimeUnit.SECONDS)));
        }
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM claim WHERE status='ACCEPTED'",Long.class));
    }
    @Test void cancellationReleasesReservation() {
        UUID item=found(),a=claim(owner,item),b=claim(other,item);
        service.decide(finder,a,"accept"); service.decide(owner,a,"cancel"); service.decide(finder,b,"accept");
        assertEquals(FoundStatus.AVAILABLE,service.found(item).status());
    }
    @Test void delegationTransfersAuthorityButNotCustody() {
        UUID item=found(),c=claim(owner,item);
        fails(403,()->service.office(other,item));
        fails(400,()->service.delegate(finder,item,other));
        service.delegate(finder,item,teacher);
        fails(403,()->service.decide(finder,c,"accept")); service.decide(teacher,c,"accept");
        assertEquals(CustodyStatus.WITH_FINDER,service.found(item).custody());
    }
    @Test void claimsArePrivateAndOwnershipIsChecked() {
        UUID item=found(); claim(owner,item);
        assertTrue(service.claims(other,0).isEmpty()); assertEquals(1,service.claims(finder,0).size());
        fails(409,()->claim(finder,item));
        UUID loss=service.createLost(owner,report());
        fails(403,()->service.claim(other,item,new ClaimRequestDto("Detalles privados de prueba",loss)));
        fails(403,()->service.recoverLost(other,loss));
    }
    @Test void duplicateClaimsAreRejectedAndLossCanCloseExternally() {
        UUID item=found(); claim(owner,item);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->claim(owner,item));
        UUID loss=service.createLost(owner,report()); service.recoverLost(owner,loss);
        assertEquals("RECOVERED",db.queryForObject("SELECT status FROM lost_report WHERE id=?",String.class,loss));
    }
    @Test void simultaneousRecoveryCreatesOnlyOneEvent() throws Exception {
        UUID item = found(), c = claim(owner, item);
        service.decide(finder, c, "accept");
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Void> complete = () -> { gate.await(); service.decide(owner, c, "complete"); return null; };
            var first = executor.submit(complete);
            var second = executor.submit(complete);
            gate.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertEquals(1L, db.queryForObject("SELECT count(*) FROM email_outbox", Long.class));
        assertEquals(FoundStatus.RETURNED, service.found(item).status());
    }
}
