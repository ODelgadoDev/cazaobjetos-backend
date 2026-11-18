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
    public ItemEventService(AuditEventRepository audit) {
        this.audit = audit;
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
}
