package mx.utch.cazaobjetos.service;

import java.io.*;
import java.time.Instant;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import mx.utch.cazaobjetos.dto.PhotoResponseDto;
import mx.utch.cazaobjetos.model.PublicationPhotoModel;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.exception.BusinessException;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;

@Service
@Transactional
public class PhotoService {
    private static final int MAX_BYTES=5*1024*1024;
    private final PhotoDataService photos;
    private final ItemDataService items;
    private final ItemEventService events;
    public PhotoService(PhotoDataService photos, ItemDataService items, ItemEventService events) {
        this.photos=photos;this.items=items;this.events=events;
    }
    private void access(boolean found,UUID parent,UUID actor,boolean write) {
        // Parent locks serialize upload count, withdrawal, delegation and claim creation.
        if(found) {
            var item=items.lockItem(parent);
            if(write) {
                require(actor.equals(item.getResponsibleId()),HttpStatus.FORBIDDEN,"Solo el responsable puede modificar las fotos");
                require(item.getStatus()==FoundStatus.AVAILABLE,HttpStatus.CONFLICT,"El hallazgo no está disponible");
                require(!items.hasActiveClaims(parent),HttpStatus.CONFLICT,"Resuelve o cancela los reclamos activos primero");
            } else require(item.getStatus()!=FoundStatus.WITHDRAWN || actor.equals(item.getReporterId()) || actor.equals(item.getResponsibleId()),HttpStatus.NOT_FOUND,"Foto no disponible");
        } else {
            var loss=items.lockLoss(parent);
            if(write) {
                require(actor.equals(loss.getOwnerId()),HttpStatus.FORBIDDEN,"Solo el dueño puede modificar las fotos");
                require(loss.getStatus()==LostStatus.ACTIVE,HttpStatus.CONFLICT,"El extravío no está activo");
                require(!items.hasActiveLinkedClaim(parent),HttpStatus.CONFLICT,"Resuelve o cancela el reclamo vinculado primero");
            } else require(loss.getStatus()!=LostStatus.WITHDRAWN || actor.equals(loss.getOwnerId()),HttpStatus.NOT_FOUND,"Foto no disponible");
        }
    }
    public List<PhotoResponseDto> list(boolean found,UUID parent,UUID actor) {
        access(found,parent,actor,false);return photos.list(parent);
    }
    private PublicationPhotoModel scoped(boolean found,UUID parent,UUID id) {
        var photo=photos.photo(id);
        require(parent.equals(found?photo.getFoundItemId():photo.getLostReportId()),HttpStatus.NOT_FOUND,"Foto no disponible");
        return photo;
    }
    public PublicationPhotoModel content(boolean found,UUID parent,UUID id,UUID actor) {
        access(found,parent,actor,false);return scoped(found,parent,id);
    }
    public void delete(boolean found,UUID parent,UUID id,UUID actor) {
        access(found,parent,actor,true);photos.delete(scoped(found,parent,id));events.audit(actor,parent,"PHOTO_DELETED_"+id);
    }
    public void checkUpload(boolean found, UUID parent, UUID actor) {
        access(found,parent,actor,true);
        require(photos.count(parent)<3,HttpStatus.CONFLICT,"Máximo 3 fotos por publicación");
    }
    public PhotoResponseDto upload(boolean found,UUID parent,UUID actor,MultipartFile file) {
        return upload(found,parent,actor,file,false);
    }
    public PhotoResponseDto upload(boolean found,UUID parent,UUID actor,MultipartFile file,boolean referenceImage) {
        access(found,parent,actor,true);
        require(photos.count(parent)<3,HttpStatus.CONFLICT,"Máximo 3 fotos por publicación");
        require(!file.isEmpty(),HttpStatus.BAD_REQUEST,"Selecciona una imagen");
        require(file.getSize()<=MAX_BYTES,HttpStatus.PAYLOAD_TOO_LARGE,"La foto excede 5 MB");
        var image=normalize(file);
        var photo=new PublicationPhotoModel();photo.setId(UUID.randomUUID());
        if(found) photo.setFoundItemId(parent);else photo.setLostReportId(parent);
        photo.setContentType(image.type());photo.setContent(image.bytes());photo.setSizeBytes(image.bytes().length);photo.setCreatedAt(Instant.now());photo.setReferenceImage(!found && referenceImage);
        photos.save(photo);events.audit(actor,parent,"PHOTO_ADDED_"+photo.getId());
        return new PhotoResponseDto(photo.getId(),photo.getContentType(),photo.getSizeBytes(),photo.getCreatedAt(),photo.isReferenceImage());
    }
    private record Image(String type,byte[] bytes) {}
    private Image normalize(MultipartFile file) {
        try(var raw=file.getInputStream();var input=new MemoryCacheImageInputStream(raw)) {
            var readers=ImageIO.getImageReaders(input);
            require(readers.hasNext(),HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Solo se admiten imágenes JPEG o PNG");
            var reader=readers.next();
            try {
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                require(Set.of("jpeg","jpg","png").contains(format),HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Solo se admiten imágenes JPEG o PNG");
                reader.setInput(input,true,true);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                require(width>0 && height>0 && (long)width*height<=12_000_000 && width<=6000 && height<=6000,HttpStatus.BAD_REQUEST,"Máximo 12 megapíxeles y 6000 píxeles por lado");
                var decoded=reader.read(0);
                // Re-encoding excludes original metadata, names and appended payloads.
                var output=new ByteArrayOutputStream();
                require(ImageIO.write(decoded,format,output),HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Imagen no compatible");
                require(output.size()<=MAX_BYTES,HttpStatus.PAYLOAD_TOO_LARGE,"La imagen procesada excede 5 MB");
                return new Image(format.equals("png")?"image/png":"image/jpeg",output.toByteArray());
            } finally { reader.dispose(); }
        } catch(IOException | IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,"La imagen está dañada o no es válida");
        }
    }
}
