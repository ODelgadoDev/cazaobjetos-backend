package mx.utch.cazaobjetos.config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import mx.utch.cazaobjetos.service.*;
@Configuration
@EnableScheduling
@ConditionalOnProperty(name={"app.push.enabled","app.push.worker-enabled"},havingValue="true",matchIfMissing=true)
public class PushWorker {
    private final PushDeliveryService deliveries;private final PushReminderService reminders;
    public PushWorker(PushDeliveryService deliveries,PushReminderService reminders){this.deliveries=deliveries;this.reminders=reminders;}
    @Scheduled(fixedDelay=5000,initialDelay=10000) public void deliveries(){deliveries.deliver();}
    @Scheduled(fixedDelay=60000,initialDelay=15000) public void reminders(){reminders.generate();}
}
