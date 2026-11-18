package mx.utch.cazaobjetos.service;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import mx.utch.cazaobjetos.dto.FoundPublicationRequestDto;
import mx.utch.cazaobjetos.enums.LostStatus;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;
@Service
@Transactional(propagation=Propagation.MANDATORY)
public class FoundPublicationService {
    private final mx.utch.cazaobjetos.repository.FoundItemRepository found;
    private final ItemService items;
    private final ItemDataService data;
    private final PhotoService photos;
    private final NotificationEventService notifications;
    private final ItemEventService audit;
    public FoundPublicationService(ItemService items,ItemDataService data,PhotoService photos,NotificationEventService notifications,ItemEventService audit,mx.utch.cazaobjetos.repository.FoundItemRepository found) {this.items=items;this.data=data;this.photos=photos;this.notifications=notifications;this.audit=audit;this.found=found;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public mx.utch.cazaobjetos.dto.PageResponseDto<mx.utch.cazaobjetos.dto.FoundItemResponseDto> matches(UUID actor,UUID source,int page,int size) {
        require(actor.equals(data.lost(source).getOwnerId()),HttpStatus.NOT_FOUND,"Reporte no disponible");
        require(page>=0&&page<=10000&&size>=1&&size<=100,HttpStatus.BAD_REQUEST,"Paginación inválida");
        var paging=org.springframework.data.domain.PageRequest.of(page,size,org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("createdAt"),org.springframework.data.domain.Sort.Order.asc("id")));
        return mx.utch.cazaobjetos.dto.PageResponseDto.from(found.findBySourceLostReportIdAndStatusNot(source,mx.utch.cazaobjetos.enums.FoundStatus.WITHDRAWN,paging).map(item->item.toResponseDto()));
    }
    public UUID publish(UUID actor,FoundPublicationRequestDto request,MultipartFile file) {
        require(request.report()!=null&&request.custody()!=null,HttpStatus.BAD_REQUEST,"Completa la publicación y el resguardo");
        UUID owner=null;
        if(request.sourceLostReportId()!=null) {
            // Source first, then the new unpublished found item. Concurrent closure is serialized.
            var loss=data.lockLoss(request.sourceLostReportId());
            require(loss.getStatus()!=LostStatus.WITHDRAWN,HttpStatus.NOT_FOUND,"El reporte original no está disponible");
            require(loss.getStatus()==LostStatus.ACTIVE,HttpStatus.CONFLICT,"El extravío original ya se cerró. Revisa el reporte antes de publicar");
            require(!actor.equals(loss.getOwnerId()),HttpStatus.CONFLICT,"Este es tu propio reporte. Si recuperaste el objeto, ciérralo desde Tu reporte");owner=loss.getOwnerId();
        }
        UUID id=items.createFound(actor,request.report());
        data.found(id).setSourceLostReportId(request.sourceLostReportId());
        items.custody(actor,id,request.custody());
        photos.upload(true,id,actor,file);
        if(owner!=null) {audit.audit(actor,id,"FOUND_SOURCE_LINKED");notifications.possibleMatch(owner,id);}
        return id;
    }
}
