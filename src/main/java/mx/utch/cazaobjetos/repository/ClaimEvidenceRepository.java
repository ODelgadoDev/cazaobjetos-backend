package mx.utch.cazaobjetos.repository;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import mx.utch.cazaobjetos.model.ClaimEvidenceModel;
import mx.utch.cazaobjetos.dto.ClaimEvidenceDto;
public interface ClaimEvidenceRepository extends JpaRepository<ClaimEvidenceModel,UUID> {
    long countByClaimId(UUID claimId);
    @Query("select new mx.utch.cazaobjetos.dto.ClaimEvidenceDto(e.id,e.authorId,e.contentType,e.sizeBytes,e.createdAt,e.referenceImage) from ClaimEvidenceModel e where e.claimId=:claimId order by e.createdAt,e.id")
    List<ClaimEvidenceDto> list(@org.springframework.data.repository.query.Param("claimId") UUID claimId);
}
