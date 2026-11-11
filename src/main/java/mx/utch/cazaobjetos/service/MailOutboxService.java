package mx.utch.cazaobjetos.service;

import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import mx.utch.cazaobjetos.enums.EmailStatus;
import mx.utch.cazaobjetos.repository.EmailOutboxRepository;

@Service
public class MailOutboxService {
    @org.springframework.beans.factory.annotation.Value("${app.mail.from:cazaobjetos@localhost}")
    private String from;
    private final EmailOutboxRepository outbox;
    private final ObjectProvider<JavaMailSender> senders;
    public MailOutboxService(EmailOutboxRepository outbox, ObjectProvider<JavaMailSender> senders) {
        this.outbox = outbox;
        this.senders = senders;
    }

    @Transactional
    public void deliverBatch() {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null) return;
        for (var email : outbox.lockDueBatch()) {
            email.setAttempts(email.getAttempts() + 1);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(email.getRecipient());
            message.setSubject(email.getSubject());
            message.setText(email.getBody());
            try {
                sender.send(message);
                email.setStatus(EmailStatus.SENT);
                email.setSentAt(Instant.now());
            } catch (MailException failure) {
                // Do not log message bodies, tokens, recipients or transport credentials.
                if (email.getAttempts() >= 5) email.setStatus(EmailStatus.FAILED);
                email.setNextAttemptAt(Instant.now().plusSeconds(30L * email.getAttempts()));
            }
        }
    }
}
