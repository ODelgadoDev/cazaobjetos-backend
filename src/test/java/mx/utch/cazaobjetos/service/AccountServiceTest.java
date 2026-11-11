package mx.utch.cazaobjetos.service;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import mx.utch.cazaobjetos.dto.RegisterRequestDto;
import mx.utch.cazaobjetos.exception.BusinessException;
import mx.utch.cazaobjetos.util.VerificationTokenUtil;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AccountServiceTest {
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    private static final String EMAIL = "prueba@utch.edu.mx";
    @BeforeEach void reset() {
        db.execute("TRUNCATE email_verification,email_outbox,audit_event,claim,lost_report,found_item,app_user CASCADE");
    }
    void register() { accounts.register(new RegisterRequestDto("Prueba@UTCH.EDU.MX", "contraseña-pruebas-123", "Ana")); }
    String token() {
        String body = db.queryForObject("SELECT body FROM email_outbox ORDER BY created_at DESC LIMIT 1", String.class);
        return Arrays.stream(body.split("\\R")).filter(line -> line.matches("[A-Za-z0-9_-]{43}")).findFirst().orElseThrow();
    }
    void fails(int status, Runnable action) {
        assertEquals(status, assertThrows(BusinessException.class, action::run).getStatusCode().value());
    }
    @Test void registrationIsDisabledAndDoesNotStorePlainPasswordOrPlainTokenInVerification() {
        register();
        var user = db.queryForMap("SELECT * FROM app_user WHERE email=?", EMAIL);
        assertEquals(false, user.get("enabled"));
        assertEquals("STUDENT", user.get("role"));
        assertTrue(passwords.matches("contraseña-pruebas-123", (String)user.get("password_hash")));
        assertEquals(VerificationTokenUtil.hash(token()), db.queryForObject("SELECT token_hash FROM email_verification", String.class));
    }
    @Test void verificationEnablesAccountAndRejectsReuse() {
        register(); String token = token();
        accounts.verify(token);
        assertEquals(true, db.queryForObject("SELECT enabled FROM app_user", Boolean.class));
        assertNotNull(db.queryForObject("SELECT email_verified_at FROM app_user", java.sql.Timestamp.class));
        fails(400, () -> accounts.verify(token));
    }
    @Test void expiryAndRandomTokensAreRejected() {
        register(); String token = token();
        db.update("UPDATE email_verification SET expires_at=now()-interval '1 second'");
        fails(400, () -> accounts.verify(token));
        fails(400, () -> accounts.verify("x".repeat(43)));
        assertEquals(false, db.queryForObject("SELECT enabled FROM app_user", Boolean.class));
    }
    @Test void resendHasCooldownAndInvalidatesOldToken() {
        register(); String old = token();
        accounts.resend(EMAIL);
        assertEquals(1L, db.queryForObject("SELECT count(*) FROM email_outbox", Long.class));
        db.update("UPDATE email_verification SET sent_at=now()-interval '61 seconds'");
        accounts.resend(EMAIL);
        String current = token(); assertNotEquals(old, current);
        fails(400, () -> accounts.verify(old));
        accounts.verify(current);
    }
    @Test void resendDoesNotReactivateVerifiedDisabledAccounts() {
        register(); accounts.verify(token());
        db.update("UPDATE app_user SET enabled=false");
        db.update("UPDATE email_verification SET sent_at=now()-interval '61 seconds'");
        accounts.resend(EMAIL); accounts.resend("inexistente@utch.edu.mx");
        assertEquals(1L, db.queryForObject("SELECT count(*) FROM email_outbox", Long.class));
        assertEquals(false, db.queryForObject("SELECT enabled FROM app_user", Boolean.class));
    }
    @Test void nonInstitutionalDomainAndTooManyPasswordBytesAreRejected() {
        for (String email : List.of("test@gmail.com", "test@utch.edu.mx.evil.com", "test@sub.utch.edu.mx")) {
            fails(400, () -> accounts.register(new RegisterRequestDto(email, "clave-prueba-123", "Ana")));
        }
        fails(400, () -> accounts.register(new RegisterRequestDto(EMAIL, "é".repeat(40), "Ana")));
        assertEquals(0L, db.queryForObject("SELECT count(*) FROM app_user", Long.class));
    }
    @Test void duplicateRegistrationDoesNotChangePasswordOrCreateAnotherMail() {
        register();
        fails(409, () -> accounts.register(new RegisterRequestDto(EMAIL,"otra-clave-123", "Otro")));
        assertEquals(1L, db.queryForObject("SELECT count(*) FROM email_outbox", Long.class));
    }
    @Test void concurrentVerificationOnlySucceedsOnce() throws Exception {
        register(); String token = token(); var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> verify = () -> { gate.await(); try { accounts.verify(token); return 204; }
                catch (BusinessException e) { return e.getStatusCode().value(); } };
            var first = executor.submit(verify); var second = executor.submit(verify); gate.countDown();
            assertEquals(Set.of(204,400), Set.of(first.get(10, TimeUnit.SECONDS),second.get(10, TimeUnit.SECONDS)));
        }
    }
}
