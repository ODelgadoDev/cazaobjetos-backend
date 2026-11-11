package mx.utch.cazaobjetos.repository;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import mx.utch.cazaobjetos.model.EmailVerificationModel;

public interface EmailVerificationRepository extends JpaRepository<EmailVerificationModel, UUID> {
    @Query("select v.userId from EmailVerificationModel v where v.tokenHash=:hash")
    Optional<UUID> findUserIdByTokenHash(@Param("hash") String hash);
}
