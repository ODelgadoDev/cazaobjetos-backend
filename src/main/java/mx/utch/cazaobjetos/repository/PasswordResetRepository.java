package mx.utch.cazaobjetos.repository;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import mx.utch.cazaobjetos.model.PasswordResetModel;

public interface PasswordResetRepository extends JpaRepository<PasswordResetModel, UUID> {
    @Query("select v.userId from PasswordResetModel v where v.tokenHash=:hash")
    Optional<UUID> findUserIdByTokenHash(@Param("hash") String hash);
}
