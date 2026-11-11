package mx.utch.cazaobjetos.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.mail.javamail.*;
import org.springframework.scheduling.annotation.EnableScheduling;
/** Explicit remote SMTP profile. Never active for development or tests. */
@Configuration
@Profile("smtp & !dev & !test")
@EnableScheduling
public class SmtpConfig {
    @Bean JavaMailSender remoteMailSender(@Value("${app.smtp.host}") String host,
            @Value("${app.smtp.port:587}") int port,@Value("${app.smtp.username}") String username,
            @Value("${app.smtp.password}") String password,@Value("${app.mail.from}") String from) {
        if(host.isBlank() || username.isBlank() || password.isBlank() || !from.matches("[^\\s@]+@[^\\s@]+"))
            throw new IllegalArgumentException("Falta configuración SMTP válida");
        var sender=new JavaMailSenderImpl();sender.setHost(host);sender.setPort(port);
        sender.setUsername(username);sender.setPassword(password);sender.setDefaultEncoding("UTF-8");
        var p=sender.getJavaMailProperties();p.setProperty("mail.smtp.auth","true");
        p.setProperty("mail.smtp.starttls.enable","true");p.setProperty("mail.smtp.starttls.required","true");
        p.setProperty("mail.smtp.ssl.checkserveridentity","true");
        p.setProperty("mail.smtp.connectiontimeout","5000");p.setProperty("mail.smtp.timeout","5000");p.setProperty("mail.smtp.writetimeout","5000");
        return sender;
    }
}
