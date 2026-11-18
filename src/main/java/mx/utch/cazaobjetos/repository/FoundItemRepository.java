package mx.utch.cazaobjetos.repository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import mx.utch.cazaobjetos.model.FoundItemModel;

@Repository
public interface FoundItemRepository extends JpaRepository<FoundItemModel, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<FoundItemModel> {
    org.springframework.data.domain.Page<FoundItemModel> findBySourceLostReportIdAndStatusNot(UUID sourceLostReportId,mx.utch.cazaobjetos.enums.FoundStatus status,org.springframework.data.domain.Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from FoundItemModel m where m.id = :id")
    Optional<FoundItemModel> findLockedById(@Param("id") UUID id);
}
