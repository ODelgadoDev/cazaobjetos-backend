package mx.utch.cazaobjetos.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import mx.utch.cazaobjetos.dto.PhotoResponseDto;
import mx.utch.cazaobjetos.model.PublicationPhotoModel;
import mx.utch.cazaobjetos.repository.PhotoRepository;
import mx.utch.cazaobjetos.exception.BusinessException;
@Service
public class PhotoDataService {
    private final PhotoRepository photos;
    public PhotoDataService(PhotoRepository photos) { this.photos=photos; }
    public List<PhotoResponseDto> list(UUID parent) { return photos.list(parent); }
    public long count(UUID parent) { return photos.countByFoundItemIdOrLostReportId(parent,parent); }
    public PublicationPhotoModel photo(UUID id) { return photos.findById(id).orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"Foto no disponible")); }
    public void save(PublicationPhotoModel photo) { photos.save(photo); }
    public void delete(PublicationPhotoModel photo) { photos.delete(photo); }
}
