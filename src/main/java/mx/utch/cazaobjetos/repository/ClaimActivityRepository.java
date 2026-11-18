package mx.utch.cazaobjetos.repository;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import mx.utch.cazaobjetos.model.ClaimActivityModel;
import mx.utch.cazaobjetos.enums.ClaimActivityKind;
public interface ClaimActivityRepository extends JpaRepository<ClaimActivityModel,UUID> {
    Page<ClaimActivityModel> findByClaimId(UUID claimId,Pageable page);
    Optional<ClaimActivityModel> findByClaimIdAndActorIdAndKindAndRequestKey(UUID claimId,UUID actorId,ClaimActivityKind kind,String requestKey);
}
