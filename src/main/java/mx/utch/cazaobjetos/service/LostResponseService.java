package mx.utch.cazaobjetos.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.LostStatus;
import mx.utch.cazaobjetos.enums.NotificationKind;
import mx.utch.cazaobjetos.exception.BusinessException;
import mx.utch.cazaobjetos.model.LostReportModel;
import mx.utch.cazaobjetos.repository.LostResponseRepository;
import mx.utch.cazaobjetos.util.EvidenceImageUtil.Image;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static mx.utch.cazaobjetos.util.ValidateUtil.require;

@Service
@Transactional
public class LostResponseService {
    private final ItemDataService items;
    private final ItemService publications;
    private final LostResponseRepository responses;
    private final CreationService creations;
    private final UserDataService users;
    private final NotificationEventService notifications;

    public LostResponseService(ItemDataService items, ItemService publications, LostResponseRepository responses,
            CreationService creations, UserDataService users, NotificationEventService notifications) {
        this.items=items;
        this.publications=publications;
        this.responses=responses;
        this.creations=creations;
        this.users=users;
        this.notifications=notifications;
    }

    @Transactional(readOnly=true)
    public List<LostResponseDto> list(UUID loss, UUID actor) {
        var report=items.lost(loss);
        require(report.getStatus()!=LostStatus.WITHDRAWN || actor.equals(report.getOwnerId()) || responses.exists(loss,actor,false),
            HttpStatus.NOT_FOUND,"Reporte no disponible");
        return responses.visible(loss,actor);
    }

    public LostResponseDto create(UUID lossId, UUID finder, String key, LostResponseRequestDto request, Image photo) {
        String location=request.location().strip();
        String body=request.body().strip();
        UUID id=creations.run(finder,"loss-response:"+lossId,key,
            Map.of("location",location,"body",body,"image",photo==null?"":photo.hash()),()->{
                var loss=items.lockLoss(lossId);
                require(loss.getStatus()==LostStatus.ACTIVE,HttpStatus.CONFLICT,"El reporte está cerrado");
                require(!loss.getOwnerId().equals(finder),HttpStatus.CONFLICT,"Este es tu reporte");
                require(!responses.exists(lossId,finder,true),HttpStatus.CONFLICT,"Ya enviaste una respuesta para este objeto");
                UUID response=UUID.randomUUID();
                responses.insert(response,lossId,finder,location,body,photo);
                notify(loss.getOwnerId(),finder,loss,response,response,NotificationKind.POSSIBLE_MATCH,"Reportó un posible hallazgo");
                return response;
            });
        return participant(lossId,id,finder);
    }

    private LostResponseDto participant(UUID loss, UUID response, UUID actor) {
        return responses.find(loss,response).filter(r->actor.equals(r.finderId())||actor.equals(r.ownerId()))
            .orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"Respuesta no disponible"));
    }

    @Transactional(readOnly=true)
    public LostResponseRepository.Photo photo(UUID loss, UUID response, UUID actor) {
        participant(loss,response,actor);
        return responses.photo(response).orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"Foto no disponible"));
    }

    @Transactional(readOnly=true)
    public List<LostResponseCommentDto> comments(UUID loss, UUID response, UUID actor) {
        participant(loss,response,actor);
        return responses.comments(response);
    }

    public LostResponseCommentDto comment(UUID lossId, UUID response, UUID actor, String key, String body) {
        String message=body.strip();
        UUID id=creations.run(actor,"loss-reply:"+response,key,Map.of("loss",lossId,"body",message),()->{
            var loss=items.lockLoss(lossId);
            var row=participant(lossId,response,actor);
            require(loss.getStatus()==LostStatus.ACTIVE && row.status().equals("OPEN"),HttpStatus.CONFLICT,"La conversación está cerrada");
            UUID commentId=UUID.randomUUID();
            responses.insertComment(commentId,response,actor,message);
            UUID recipient=actor.equals(row.finderId())?row.ownerId():row.finderId();
            notify(recipient,actor,loss,response,commentId,NotificationKind.COMMENT,"Comentó");
            return commentId;
        });
        participant(lossId,response,actor);
        return responses.comment(response,id);
    }

    public LostResponseDto decide(UUID lossId, UUID responseId, UUID actor, boolean complete) {
        var loss=items.lockLoss(lossId);
        var response=participant(lossId,responseId,actor);
        require(actor.equals(complete?response.ownerId():response.finderId()),HttpStatus.FORBIDDEN,
            complete?"Solo quien perdió el objeto puede confirmar la entrega":"Solo quien respondió puede cancelar");
        String status=complete?"COMPLETED":"CANCELLED";
        if(response.status().equals(status)) return response;
        require(loss.getStatus()==LostStatus.ACTIVE && response.status().equals("OPEN"),HttpStatus.CONFLICT,"La respuesta está cerrada");
        responses.changeStatus(responseId,status);
        if(complete) publications.recoverLost(actor,lossId);
        notify(complete?response.finderId():response.ownerId(),actor,loss,responseId,responseId,
            complete?NotificationKind.COMPLETED:NotificationKind.CANCELLED,complete?"Confirmó la entrega":"Canceló la respuesta");
        return participant(lossId,responseId,actor);
    }

    private void notify(UUID recipient, UUID actor, LostReportModel loss, UUID response, UUID event,
            NotificationKind kind, String action) {
        notifications.lostResponse(recipient,loss.getId(),response,event,kind,action,
            users.user(actor).getDisplayName(),loss.getTitle());
    }
}
