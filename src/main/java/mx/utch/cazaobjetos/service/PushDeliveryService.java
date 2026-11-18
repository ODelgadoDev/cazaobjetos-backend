package mx.utch.cazaobjetos.service;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import mx.utch.cazaobjetos.repository.NotificationRepository;
@Service
@Transactional
public class PushDeliveryService {
    private final PushDataService data;private final NotificationRepository notices;private final PushReminderService reminders;private final PushTransport transport;private final Clock clock;private final ObjectMapper json;
    public PushDeliveryService(PushDataService data,NotificationRepository notices,PushReminderService reminders,PushTransport transport,Clock clock,ObjectMapper json){this.data=data;this.notices=notices;this.reminders=reminders;this.transport=transport;this.clock=clock;this.json=json;}
    public void deliver(){
        for(var job:data.due(clock.instant())) {
            Instant now=clock.instant();var sub=data.lock(job.subscription()).orElse(null);
            boolean valid=sub!=null&&sub.active()&&sub.owner().equals(job.owner())&&sub.binding().equals(job.binding())&&data.credentialsValid(sub)&&now.isBefore(job.expires());
            if(valid&&job.kind().equals("EVENT"))valid=notices.visibleById(job.owner(),job.notification()).map(n->n.toResponseDto().readAt()==null).orElse(false);
            if(valid&&job.kind().equals("REMINDER"))valid=data.preference(job.owner())>=(job.eventKey().endsWith(":2")?2:1)&&reminders.hasReason(job.owner());
            if(!valid){data.finish(job.id(),"CANCELLED",job.attempts(),null,now,now);continue;}
            var payload=new LinkedHashMap<String,Object>();payload.put("binding",job.binding());payload.put("deliveryId",job.id());payload.put("kind",job.kind());if(job.notification()!=null)payload.put("notificationId",job.notification());
            int attempt=job.attempts()+1;Integer status=null;long retry=0;
            try{var result=transport.send(sub,json.writeValueAsBytes(payload));status=result.status();retry=result.retryAfterSeconds();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}catch(Exception failure){/* No endpoint, keys, payload or exception text is logged. */}
            if(status!=null&&status>=200&&status<300)data.finish(job.id(),"SENT",attempt,status,now,now);
            else if(status!=null&&(status==404||status==410)){data.disable(sub.id());data.finish(job.id(),"CANCELLED",attempt,status,now,now);}
            else {
                boolean permanent=status!=null&&status>=400&&status<500&&status!=401&&status!=403&&status!=408&&status!=429;
                data.finish(job.id(),permanent||attempt>=5?"FAILED":"PENDING",attempt,status,now,now.plusSeconds(Math.max(retry,30L<<(attempt-1))));
            }
        }
    }
}
