package mx.utch.cazaobjetos.service;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.model.ClaimActivityModel;
import mx.utch.cazaobjetos.repository.NotificationRepository;
@Service
@Transactional(propagation=Propagation.MANDATORY)
public class NotificationEventService {
    private final NotificationRepository notifications;
    private final ItemDataService items;
    private final PushQueueService push;
    public NotificationEventService(NotificationRepository notifications,ItemDataService items,PushQueueService push) {this.notifications=notifications;this.items=items;this.push=push;}
    private void emit(UUID recipient,String key,UUID activity,NotificationKind kind,NotificationTarget type,UUID target,String title,String body,Instant at) {
        UUID id=UUID.randomUUID();if(notifications.insert(id,recipient,key,activity,kind.name(),type.name(),target,title,body,at)==1)push.notification(recipient,id);
    }
    public void published(UUID actor,UUID publication,boolean found) {
        emit(actor,(found?"found:":"loss:")+publication,null,found?NotificationKind.FOUND_PUBLISHED:NotificationKind.LOSS_PUBLISHED,found?NotificationTarget.FOUND_ITEM:NotificationTarget.LOST_REPORT,publication,found?"Hallazgo publicado":"Extravío publicado",found?"Tu hallazgo ya aparece en Explorar. Puedes gestionar las solicitudes desde Reclamos.":"Tu reporte de extravío ya está publicado. Revisa Explorar para buscar tu objeto.",Instant.now());
    }
    public void possibleMatch(UUID owner,UUID found) {
        emit(owner,"possible-match:"+found,null,NotificationKind.POSSIBLE_MATCH,NotificationTarget.FOUND_ITEM,found,
            "Posible hallazgo para tu extravío","Alguien publicó un hallazgo desde tu reporte. Compara la ficha y decide si es tu objeto.",Instant.now());
    }
    public void activity(ClaimActivityModel entry) {
        if(entry.getKind()==ClaimActivityKind.HISTORY_IMPORTED)return;
        var claim=items.claim(entry.getClaimId());var found=items.found(claim.getFoundItemId());
        var recipients=new LinkedHashSet<UUID>();
        switch(entry.getKind()) {
            case SUBMITTED,COMPLETED -> {recipients.add(claim.getClaimantId());recipients.add(found.getResponsibleId());}
            case CLOSED -> recipients.add(claim.getClaimantId());
            default -> {recipients.add(claim.getClaimantId());recipients.add(found.getResponsibleId());recipients.remove(entry.getActorId());}
        }
        for(UUID recipient:recipients) {
            boolean claimant=recipient.equals(claim.getClaimantId());
            String title=switch(entry.getKind()) {
                case SUBMITTED -> claimant?"Reclamo enviado":"Nuevo reclamo por revisar";
                case ACCEPTED -> "Reclamo aceptado";
                case REJECTED -> "Reclamo rechazado";
                case CANCELLED -> "Reclamo cancelado";
                case COMPLETED -> "Entrega confirmada";
                case CLOSED -> "Solicitud cerrada";
                case COMMENT -> "Nuevo comentario";
                case EVIDENCE_ADDED -> "Nueva evidencia";
                case CUSTODY_CHANGED -> "Cambió el resguardo";
                case PUBLICATION_EDITED -> "Ficha actualizada";
                case DELEGATED -> "Responsable actualizado";
                default -> "Actualización del reclamo";
            };
            String body=switch(entry.getKind()) {
                case SUBMITTED -> claimant?"Tu solicitud quedó registrada. Continúa el trámite en el expediente.":"Recibiste una solicitud. Abre el expediente para revisar sus pruebas.";
                case ACCEPTED -> "Tu solicitud fue aceptada. Acuerden la entrega en los comentarios.";
                case REJECTED -> "El responsable rechazó la solicitud. Consulta el motivo en el expediente.";
                case CANCELLED -> "El solicitante canceló su solicitud. El historial sigue disponible.";
                case COMPLETED -> "Se confirmó la recepción del objeto. El expediente quedó finalizado.";
                case CLOSED -> "El hallazgo fue entregado y esta solicitud quedó cerrada.";
                case COMMENT -> "Hay un comentario nuevo. Abre el expediente para consultarlo.";
                case EVIDENCE_ADDED -> "Se adjuntó una imagen de prueba. Revísala dentro del expediente.";
                case CUSTODY_CHANGED -> "Se actualizó dónde está el objeto. Consulta el resguardo antes de recogerlo.";
                case PUBLICATION_EDITED -> "Se corrigieron datos de una publicación vinculada al reclamo.";
                case DELEGATED -> "La gestión del hallazgo fue actualizada. Consulta tu expediente.";
                default -> "Consulta el estado actual del trámite.";
            };
            emit(recipient,"activity:"+entry.getId(),entry.getId(),NotificationKind.valueOf(entry.getKind().name()),NotificationTarget.CLAIM,entry.getClaimId(),title,body,entry.getCreatedAt());
        }
    }
}
