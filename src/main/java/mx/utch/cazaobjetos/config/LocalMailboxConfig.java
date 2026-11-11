package mx.utch.cazaobjetos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.mail.javamail.*;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Development-only SMTP sink. Remote SMTP/real email delivery is intentionally not configured. */
@Configuration
@Profile("dev")
@ConditionalOnProperty(name = "app.local-mail.enabled", havingValue = "true")
@EnableScheduling
public class LocalMailboxConfig {
    @Bean
    JavaMailSender localMailSender(@Value("${app.local-mail.port:1025}") int port) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        var properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.connectiontimeout", "3000");
        properties.setProperty("mail.smtp.timeout", "3000");
        properties.setProperty("mail.smtp.writetimeout", "3000");
        return sender;
    }
}
