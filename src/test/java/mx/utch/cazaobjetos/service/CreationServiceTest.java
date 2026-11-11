package mx.utch.cazaobjetos.service;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@ActiveProfiles("test")
class CreationServiceTest {
    @Autowired CreationService service;
    @Autowired ItemService items;
    @Autowired JdbcTemplate db;
    UUID actor,other;
    @BeforeEach void setup() {db.execute("TRUNCATE app_user CASCADE");actor=user("actor");other=user("other");}
    UUID user(String name) {UUID id=UUID.randomUUID();db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled) VALUES(?,?,?,?,?,true)",id,name+"@utch.edu.mx","unused",name,"STUDENT");return id;}
    ReportRequestDto request(String title) {return new ReportRequestDto(title,"Descripción",Category.OTHER,"I",LocalDate.now());}
    @Test void retriesReturnSameIdButOtherPayloadConflicts() {
        var r=request("Mochila");UUID id=service.found(actor,"request-001",r);
        assertEquals(id,service.found(actor,"request-001",r));
        assertEquals(409,assertThrows(BusinessException.class,()->service.found(actor,"request-001",request("Otro"))).getStatusCode().value());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM found_item",Integer.class));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM audit_event",Integer.class));
        assertEquals(400,assertThrows(BusinessException.class,()->service.lost(actor,"bad",r)).getStatusCode().value());
    }
    @Test void keyScopeIncludesActorAndOperationAndNoKeyRetainsPriorBehavior() {
        var r=request("Objeto");UUID a=service.found(actor,"request-001",r);
        assertNotEquals(a,service.found(other,"request-001",r));assertNotEquals(a,service.lost(actor,"request-001",r));
        assertNotEquals(service.found(actor,null,r),service.found(actor,null,r));
    }
    @Test void failedClaimDoesNotConsumeKeyAndSuccessfulClaimCanBeReplayedAfterCompletion() {
        UUID id=service.found(actor,null,request("Objeto"));var r=new ClaimRequestDto("Descripción privada",null);
        assertThrows(BusinessException.class,()->service.claim(actor,id,"claim-001",r));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM creation_request",Integer.class));
        UUID claim=service.claim(other,id,"claim-001",r);items.decide(actor,claim,"accept");items.decide(other,claim,"complete");
        assertEquals(claim,service.claim(other,id,"claim-001",r));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM claim",Integer.class));
    }
    @Test void concurrentRetriesCreateOnePublication() throws Exception {
        var gate=new CountDownLatch(1);var r=request("Objeto");
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<UUID> create=()->{gate.await();return service.found(actor,"parallel-001",r);};
            var a=executor.submit(create);var b=executor.submit(create);gate.countDown();assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }
        assertEquals(1,db.queryForObject("SELECT count(*) FROM found_item",Integer.class));
    }
}
