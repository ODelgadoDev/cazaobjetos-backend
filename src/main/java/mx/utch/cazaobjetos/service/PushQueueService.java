package mx.utch.cazaobjetos.service;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
@Transactional(propagation=Propagation.MANDATORY)
public class PushQueueService {
    private final PushDataService data;private final VapidKeys keys;private final Clock clock;
    public PushQueueService(PushDataService data,VapidKeys keys,Clock clock){this.data=data;this.keys=keys;this.clock=clock;}
    public void notification(UUID user,UUID id){if(keys.enabled())data.queueNotice(user,id,clock.instant());}
}
