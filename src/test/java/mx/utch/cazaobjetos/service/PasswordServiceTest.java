package mx.utch.cazaobjetos.service;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import mx.utch.cazaobjetos.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@ActiveProfiles("test")
class PasswordServiceTest {
    @Autowired PasswordService service;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder encoder;
    UUID user;
    String oldPassword="Original-password-2026",newPassword="Updated-password-2026";
    @BeforeEach void setup() {
        db.execute("TRUNCATE app_user,email_outbox CASCADE");user=UUID.randomUUID();
        db.update("INSERT INTO app_user(id,email,password_hash,display_name,role,enabled,email_verified_at) VALUES (?,?,?,?,?,true,now())",user,"password@utch.edu.mx",encoder.encode(oldPassword),"Prueba","STUDENT");
    }
    String token() {return Arrays.stream(db.queryForObject("SELECT body FROM email_outbox WHERE event_key LIKE 'RESET:%' ORDER BY created_at DESC LIMIT 1",String.class).split("\\R")).filter(x->x.matches("[A-Za-z0-9_-]{43}")).findFirst().orElseThrow();}
    void fails(Runnable action){assertEquals(400,assertThrows(BusinessException.class,action::run).getStatusCode().value());}
    @Test void tokenIsSingleUseHashedAndDoesNotChangeRole() {
        service.forgot(" PASSWORD@UTCH.EDU.MX ");String token=token();
        assertNotEquals(token,db.queryForObject("SELECT token_hash FROM password_reset",String.class));
        service.reset(token,newPassword);fails(()->service.reset(token,oldPassword));
        assertTrue(encoder.matches(newPassword,db.queryForObject("SELECT password_hash FROM app_user",String.class)));
        assertEquals(1L,db.queryForObject("SELECT credentials_version FROM app_user",Long.class));
        assertEquals("STUDENT",db.queryForObject("SELECT role FROM app_user",String.class));
    }
    @Test void unknownDisabledAndCooldownDoNotSendExtraMail() {
        service.forgot("unknown@utch.edu.mx");assertEquals(0,db.queryForObject("SELECT count(*) FROM email_outbox",Integer.class));
        service.forgot("password@utch.edu.mx");service.forgot("password@utch.edu.mx");assertEquals(1,db.queryForObject("SELECT count(*) FROM email_outbox",Integer.class));
        db.update("UPDATE app_user SET enabled=false");fails(()->service.reset(token(),newPassword));
        db.update("UPDATE password_reset SET sent_at=now()-interval '2 minutes'");service.forgot("password@utch.edu.mx");assertEquals(1,db.queryForObject("SELECT count(*) FROM email_outbox",Integer.class));
    }
    @Test void expiredAndReplacedTokensFailAndUtf8LimitIsEnforced() {
        service.forgot("password@utch.edu.mx");String first=token();
        db.update("UPDATE password_reset SET expires_at=now()-interval '1 second'");fails(()->service.reset(first,newPassword));
        db.update("UPDATE password_reset SET sent_at=now()-interval '2 minutes'");service.forgot("password@utch.edu.mx");String second=token();
        fails(()->service.reset(first,newPassword));fails(()->service.reset(second,"é".repeat(40)));fails(()->service.reset(second,"short"));
        fails(()->service.reset(second,"SinSimbolo1"));
        service.reset(second,newPassword);
    }
    @Test void changeRequiresCurrentPasswordAndInvalidatesPendingReset() {
        service.forgot("password@utch.edu.mx");String token=token();
        fails(()->service.change(user,"incorrect",newPassword));
        fails(()->service.change(user,oldPassword,"SinNumero!"));
        assertEquals(0L,db.queryForObject("SELECT credentials_version FROM app_user",Long.class));
        service.change(user,oldPassword,newPassword);fails(()->service.reset(token,oldPassword));
    }
    @Test void concurrentResetOnlySucceedsOnce() throws Exception {
        service.forgot("password@utch.edu.mx");String token=token();var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<Integer> reset=()->{gate.await();try{service.reset(token,newPassword);return 204;}catch(BusinessException e){return e.getStatusCode().value();}};
            var a=executor.submit(reset);var b=executor.submit(reset);gate.countDown();assertEquals(Set.of(204,400),Set.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)));
        }
    }
}
