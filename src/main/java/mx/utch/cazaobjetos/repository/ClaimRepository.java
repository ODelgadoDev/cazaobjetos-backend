package mx.utch.cazaobjetos.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import mx.utch.cazaobjetos.enums.ClaimStatus;
import mx.utch.cazaobjetos.dto.ClaimResponseDto;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import mx.utch.cazaobjetos.model.ClaimModel;

@Repository
public interface ClaimRepository extends JpaRepository<ClaimModel, UUID> {
    @Query("select c.foundItemId from ClaimModel c where c.id = :id")
    Optional<UUID> findItemIdById(@Param("id") UUID id);
    boolean existsByFoundItemIdAndStatusIn(UUID itemId, Collection<ClaimStatus> statuses);
    boolean existsByFoundItemIdAndStatus(UUID itemId, ClaimStatus status);
    boolean existsByFoundItemIdAndClaimantIdAndStatusIn(UUID itemId, UUID userId, Collection<ClaimStatus> statuses);
    boolean existsByLostReportIdAndStatusIn(UUID lossId, Collection<ClaimStatus> statuses);
    List<ClaimModel> findByLostReportIdAndStatusIn(UUID lossId, Collection<ClaimStatus> statuses);
    List<ClaimModel> findByFoundItemIdAndStatusIn(UUID itemId, Collection<ClaimStatus> statuses);
    List<ClaimModel> findByFoundItemIdAndStatus(UUID itemId, ClaimStatus status);

    @Query("""
        select new mx.utch.cazaobjetos.dto.ClaimResponseDto(c.id,c.foundItemId,c.claimantId,c.lostReportId,
            c.privateDescription,c.status,c.acceptedBy,c.acceptedAt,c.completedAt,c.createdAt,u.email,v.email)
        from ClaimModel c join FoundItemModel f on f.id=c.foundItemId
        join UserModel u on u.id=f.responsibleId join UserModel v on v.id=c.claimantId
        where c.claimantId=:actor or f.responsibleId=:actor
        order by c.createdAt desc,c.id asc
        """)
    List<ClaimResponseDto> findVisibleTo(
        @Param("actor") UUID actor, Pageable page);
    @Query("""
        select new mx.utch.cazaobjetos.dto.ClaimResponseDto(c.id,c.foundItemId,c.claimantId,c.lostReportId,
            c.privateDescription,c.status,c.acceptedBy,c.acceptedAt,c.completedAt,c.createdAt,u.email,v.email)
        from ClaimModel c join FoundItemModel f on f.id=c.foundItemId
        join UserModel u on u.id=f.responsibleId join UserModel v on v.id=c.claimantId
        where (c.claimantId=:actor or f.responsibleId=:actor)
        and (:view='ALL' or (:view='SENT' and c.claimantId=:actor) or (:view='RECEIVED' and f.responsibleId=:actor))
        and (:status is null or c.status=:status)
        and (:found is null or c.foundItemId=:found)
        and (:id is null or c.id=:id)
        order by c.createdAt desc,c.id asc
        """)
    org.springframework.data.domain.Page<ClaimResponseDto> search(
        @Param("actor") UUID actor,@Param("view") String view,@Param("status") ClaimStatus status,
        @Param("found") UUID found,@Param("id") UUID id,Pageable page);
}
