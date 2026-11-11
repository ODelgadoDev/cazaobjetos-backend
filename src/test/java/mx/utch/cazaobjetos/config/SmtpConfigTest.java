package mx.utch.cazaobjetos.config;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import static org.junit.jupiter.api.Assertions.*;
class SmtpConfigTest {
    @Test void requiresTlsAuthenticationAndBoundedTimeoutsWithoutConnecting() {
        var sender=(JavaMailSenderImpl)new SmtpConfig().remoteMailSender("smtp.example.invalid",587,"user","password","sender@example.invalid");
        var p=sender.getJavaMailProperties();assertEquals("true",p.getProperty("mail.smtp.starttls.required"));
        assertEquals("true",p.getProperty("mail.smtp.ssl.checkserveridentity"));assertEquals("true",p.getProperty("mail.smtp.auth"));
        assertEquals("5000",p.getProperty("mail.smtp.timeout"));
        assertThrows(IllegalArgumentException.class,()->new SmtpConfig().remoteMailSender("",587,"user","password","sender@example.invalid"));
    }
}
