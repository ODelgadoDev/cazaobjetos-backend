package mx.utch.cazaobjetos.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import mx.utch.cazaobjetos.service.MailOutboxService;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.local-mail.enabled", havingValue = "true")
public class LocalMailboxWorker {
    private final MailOutboxService outbox;
    public LocalMailboxWorker(MailOutboxService outbox) { this.outbox = outbox; }
    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void deliver() { outbox.deliverBatch(); }
}
