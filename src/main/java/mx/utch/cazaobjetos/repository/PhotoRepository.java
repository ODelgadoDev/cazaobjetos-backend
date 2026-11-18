package mx.utch.cazaobjetos.repository;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import mx.utch.cazaobjetos.model.PublicationPhotoModel;
import mx.utch.cazaobjetos.dto.PhotoResponseDto;
public interface PhotoRepository extends JpaRepository<PublicationPhotoModel,UUID> {
    @Query("select new mx.utch.cazaobjetos.dto.PhotoResponseDto(p.id,p.contentType,p.sizeBytes,p.createdAt,p.referenceImage) from PublicationPhotoModel p where p.foundItemId=:parent or p.lostReportId=:parent order by p.createdAt,p.id")
    List<PhotoResponseDto> list(@Param("parent") UUID parent);
    long countByFoundItemIdOrLostReportId(UUID found, UUID lost);
}
