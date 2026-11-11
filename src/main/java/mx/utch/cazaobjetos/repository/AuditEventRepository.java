package mx.utch.cazaobjetos.repository;


import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import mx.utch.cazaobjetos.model.AuditEventModel;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEventModel, UUID> {
}
