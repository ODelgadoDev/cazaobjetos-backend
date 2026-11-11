package mx.utch.cazaobjetos.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import mx.utch.cazaobjetos.model.UserModel;

@Repository
public interface UserRepository extends JpaRepository<UserModel, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<UserModel> {
    Optional<UserModel> findByEmailAndEnabledTrue(String email);
    @org.springframework.data.jpa.repository.Query("select u.id from UserModel u where u.email=:email")
    Optional<UUID> findIdByEmail(@org.springframework.data.repository.query.Param("email") String email);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserModel u where u.id=:id")
    Optional<UserModel> findLockedById(@org.springframework.data.repository.query.Param("id") UUID id);
    boolean existsByEmail(String email);
}
