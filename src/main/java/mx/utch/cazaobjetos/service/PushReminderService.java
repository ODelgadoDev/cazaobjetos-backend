package mx.utch.cazaobjetos.service;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import mx.utch.cazaobjetos.dto.PushPreferenceDto;
import mx.utch.cazaobjetos.repository.NotificationRepository;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;
@Service
@Transactional
public class PushReminderService {
    private final PushDataService data;private final NotificationRepository notices;private final Clock clock;private final ZoneId zone;
    public PushReminderService(PushDataService data,NotificationRepository notices,Clock clock,@Value("${app.push.reminder-zone:America/Chihuahua}") String zone){this.data=data;this.notices=notices;this.clock=clock;this.zone=ZoneId.of(zone);}
    public PushPreferenceDto preference(UUID user){return new PushPreferenceDto(data.preference(user),zone.getId(),10,18);}
    public PushPreferenceDto preference(UUID user,int frequency){require(frequency>=0&&frequency<=2,HttpStatus.BAD_REQUEST,"Elige cero, uno o dos recordatorios al día");data.preference(user,frequency);return preference(user);}
    public boolean hasReason(UUID user){return notices.visibleCount(user,true)>0||data.activeWork(user);}
    public void generate(){
        var now=clock.instant();var local=now.atZone(zone);int hour=local.getHour(),slot=hour>=10&&hour<12?1:hour>=18&&hour<20?2:0;if(slot==0)return;
        for(UUID user:data.reminderUsers())if(data.preference(user)>=slot&&hasReason(user)&&data.reminderRun(user,local.toLocalDate(),slot,now))for(var sub:data.active(user))data.queue(sub,"REMINDER","reminder:"+local.toLocalDate()+":"+slot,now);
    }
}
