package mx.utch.cazaobjetos.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import mx.utch.cazaobjetos.enums.EmailStatus;
import mx.utch.cazaobjetos.model.*;
import mx.utch.cazaobjetos.repository.*;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ItemEventService {
    private final AuditEventRepository audit;
    private final EmailOutboxRepository outbox;
    public ItemEventService(AuditEventRepository audit, EmailOutboxRepository outbox) {
        this.audit = audit;
        this.outbox = outbox;
    }
    public void audit(UUID actor, UUID entity, String action) {
        AuditEventModel event = new AuditEventModel();
        event.setId(UUID.randomUUID());
        event.setActorId(actor);
        event.setEntityId(entity);
        event.setAction(action);
        event.setCreatedAt(Instant.now());
        audit.save(event);
    }
    public void recovered(FoundItemModel item, String recipient) {
        EmailOutboxModel email = new EmailOutboxModel();
        email.setId(UUID.randomUUID());
        email.setEventKey("RECOVERED:" + item.getId());
        email.setRecipient(recipient);
        email.setSubject("Objeto recuperado");
        email.setBody("El solicitante confirmó que recuperó el objeto: " + item.getTitle() + ". Folio: " + item.getId());
        email.setStatus(EmailStatus.PENDING);
        email.setCreatedAt(Instant.now());
        outbox.save(email);
    }
}
