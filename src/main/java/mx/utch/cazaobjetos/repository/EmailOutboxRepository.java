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
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value="INSERT INTO email_outbox(id,event_key,recipient,subject,body,status,created_at,next_attempt_at) VALUES(:id,:key,:recipient,:subject,:body,'PENDING',now(),now()) ON CONFLICT(event_key) DO NOTHING",nativeQuery=true)
    int enqueue(@org.springframework.data.repository.query.Param("id") UUID id,@org.springframework.data.repository.query.Param("key") String key,@org.springframework.data.repository.query.Param("recipient") String recipient,@org.springframework.data.repository.query.Param("subject") String subject,@org.springframework.data.repository.query.Param("body") String body);
}

