package mx.utch.cazaobjetos.config;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import mx.utch.cazaobjetos.service.MailOutboxService;
@Component
@Profile("smtp & !dev & !test")
public class SmtpWorker {
    private final MailOutboxService outbox;
    public SmtpWorker(MailOutboxService outbox) { this.outbox=outbox; }
    @Scheduled(fixedDelay=5000,initialDelay=5000) public void deliver() { outbox.deliverBatch(); }
}
