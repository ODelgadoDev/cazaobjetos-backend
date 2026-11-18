package mx.utch.cazaobjetos.repository;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import mx.utch.cazaobjetos.model.ProfilePhotoModel;

public interface ProfilePhotoRepository extends JpaRepository<ProfilePhotoModel, UUID> {
    @Query("select p.version from ProfilePhotoModel p where p.userId=:userId")
    Optional<UUID> findVersionByUserId(@Param("userId") UUID userId);
}
