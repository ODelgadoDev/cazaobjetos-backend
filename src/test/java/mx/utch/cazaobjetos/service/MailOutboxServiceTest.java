package mx.utch.cazaobjetos.service;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
class MailOutboxServiceTest {
    @Autowired MailOutboxService outbox;
    @Autowired JdbcTemplate db;
    @MockitoBean JavaMailSender sender;
    @BeforeEach void prepare() {
        db.execute("TRUNCATE email_outbox");
        db.update("INSERT INTO email_outbox(id,event_key,recipient,subject,body) VALUES (?,?,?,?,?)",
                UUID.randomUUID(),"TEST", "demo@utch.edu.mx", "Prueba", "Mensaje de prueba");
    }
    @Test void successfulMailIsMarkedSentAndNotSentAgain() {
        outbox.deliverBatch(); outbox.deliverBatch();
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
        assertEquals("SENT", db.queryForObject("SELECT status FROM email_outbox", String.class));
        assertNotNull(db.queryForObject("SELECT sent_at FROM email_outbox", java.sql.Timestamp.class));
    }
    @Test void failedMailBacksOffAndStopsAfterFiveAttempts() {
        doThrow(new MailSendException("Local mailbox unavailable")).when(sender).send(any(SimpleMailMessage.class));
        outbox.deliverBatch(); outbox.deliverBatch();
        verify(sender,times(1)).send(any(SimpleMailMessage.class));
        assertEquals("PENDING", db.queryForObject("SELECT status FROM email_outbox", String.class));
        for (int n=0; n<4; n++) {
            db.update("UPDATE email_outbox SET next_attempt_at=now()-interval '1 second'");
            outbox.deliverBatch();
        }
        outbox.deliverBatch();
        assertEquals("FAILED", db.queryForObject("SELECT status FROM email_outbox", String.class));
        assertEquals(5, db.queryForObject("SELECT attempts FROM email_outbox", Integer.class));
    }
}
