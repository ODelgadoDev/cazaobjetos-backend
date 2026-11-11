package mx.utch.cazaobjetos.repository;


import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import mx.utch.cazaobjetos.model.EmailOutboxModel;

@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutboxModel, UUID> {
    @org.springframework.data.jpa.repository.Query(value = """
        SELECT * FROM email_outbox WHERE status='PENDING' AND next_attempt_at<=now()
        ORDER BY created_at LIMIT 10 FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    java.util.List<EmailOutboxModel> lockDueBatch();
}

