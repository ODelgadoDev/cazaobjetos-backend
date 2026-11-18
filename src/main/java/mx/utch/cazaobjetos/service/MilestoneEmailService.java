package mx.utch.cazaobjetos.service;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import mx.utch.cazaobjetos.enums.ClaimActivityKind;
import mx.utch.cazaobjetos.model.ClaimActivityModel;
import mx.utch.cazaobjetos.repository.EmailOutboxRepository;
@Service
@Transactional(propagation=Propagation.MANDATORY)
public class MilestoneEmailService {
    private final EmailOutboxRepository outbox;
    private final UserDataService users;
    private final ItemDataService items;
    private final String frontend;
    public MilestoneEmailService(EmailOutboxRepository outbox,UserDataService users,ItemDataService items,@Value("${app.frontend-url:}") String frontend) {
        this.outbox=outbox;this.users=users;this.items=items;this.frontend=frontend.strip().replaceAll("/+$","");
        if(!this.frontend.isEmpty()) {
            URI url=URI.create(this.frontend);
            if(!Set.of("https","http").contains(url.getScheme())||url.getHost()==null||url.getUserInfo()!=null||url.getQuery()!=null||url.getFragment()!=null)throw new IllegalArgumentException("FRONTEND_URL requiere una dirección HTTP o HTTPS sin credenciales, consulta ni fragmento");
        }
    }
    private void send(UUID recipient,String event,UUID id,String subject,String text,String path) {
        String body=text+"\nFolio: "+id+"\n"+(frontend.isEmpty()?"Abre CazaObjetos para consultar el estado.":"Abrir en CazaObjetos: "+frontend+path)+"\nLas pruebas, comentarios y acuerdos se consultan dentro de la aplicación.";
        outbox.enqueue(UUID.randomUUID(),"MILESTONE:"+event+":"+id+":"+recipient,users.user(recipient).getEmail(),subject,body);
    }
    public void published(UUID actor,UUID id,boolean found) {
        send(actor,found?"FOUND_PUBLISHED":"LOSS_PUBLISHED",id,found?"Hallazgo publicado en CazaObjetos":"Extravío publicado en CazaObjetos",found?"Tu hallazgo quedó publicado correctamente.":"Tu reporte de extravío quedó publicado correctamente.",found?"/reclamo/"+id:"/extravio/"+id);
    }
    public void activity(ClaimActivityModel entry) {
        var kind=entry.getKind();
        if(!Set.of(ClaimActivityKind.SUBMITTED,ClaimActivityKind.REJECTED,ClaimActivityKind.CANCELLED,ClaimActivityKind.COMPLETED,ClaimActivityKind.CLOSED).contains(kind))return;
        var claim=items.claim(entry.getClaimId());var found=items.found(claim.getFoundItemId());
        var recipients=new LinkedHashSet<UUID>();recipients.add(claim.getClaimantId());
        if(kind!=ClaimActivityKind.CLOSED)recipients.add(found.getResponsibleId());
        if(kind==ClaimActivityKind.COMPLETED)recipients.add(found.getReporterId());
        String text=switch(kind) {
            case SUBMITTED->"Se inició una solicitud de reclamo. Revisa su expediente dentro de CazaObjetos.";
            case REJECTED->"El reclamo finalizó con rechazo. El motivo y el historial están en el expediente.";
            case CANCELLED->"El solicitante canceló el reclamo. El historial sigue disponible.";
            case CLOSED->"El hallazgo fue entregado y esta solicitud quedó cerrada.";
            default->"El solicitante confirmó que recibió físicamente el objeto. El reclamo quedó finalizado.";
        };
        for(UUID recipient:recipients) {
            boolean participant=recipient.equals(claim.getClaimantId())||recipient.equals(found.getResponsibleId());
            send(recipient,kind.name(),participant?claim.getId():found.getId(),kind==ClaimActivityKind.SUBMITTED?"Reclamo iniciado en CazaObjetos":"Reclamo finalizado en CazaObjetos",text,participant?"/reclamos/"+claim.getId():"/reclamo/"+found.getId());
        }
    }
}
