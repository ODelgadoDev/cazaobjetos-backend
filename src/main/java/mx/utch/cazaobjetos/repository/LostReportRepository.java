package mx.utch.cazaobjetos.repository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import mx.utch.cazaobjetos.model.LostReportModel;

@Repository
public interface LostReportRepository extends JpaRepository<LostReportModel, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<LostReportModel> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from LostReportModel m where m.id = :id")
    Optional<LostReportModel> findLockedById(@Param("id") UUID id);
}
