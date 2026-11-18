package mx.utch.cazaobjetos.controller;
import java.security.Principal;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.service.*;
@RestController
@RequestMapping("${url.api}/push")
public class PushController {
    private final PushSubscriptionService subscriptions;private final PushReminderService reminders;private final VapidKeys keys;private final UserDataService users;
    public PushController(PushSubscriptionService subscriptions,PushReminderService reminders,VapidKeys keys,UserDataService users){this.subscriptions=subscriptions;this.reminders=reminders;this.keys=keys;this.users=users;}
    @GetMapping("/config") public PushConfigDto config(){return new PushConfigDto(keys.enabled(),keys.publicKey());}
    @PostMapping("/subscriptions") public PushSubscriptionResponseDto register(Principal actor,HttpServletRequest request,@Valid @RequestBody PushSubscriptionRequestDto body){return subscriptions.register(users.userId(actor.getName()),request.getSession().getId(),body);}
    @DeleteMapping("/subscriptions/{id}") public ResponseEntity<Void> disable(Principal actor,@PathVariable UUID id){subscriptions.disable(users.userId(actor.getName()),id);return ResponseEntity.noContent().build();}
    @PostMapping("/test") public ResponseEntity<IdResponseDto> test(Principal actor,HttpServletRequest request,@Valid @RequestBody PushTestRequestDto body){return ResponseEntity.accepted().body(new IdResponseDto(subscriptions.test(users.userId(actor.getName()),request.getSession().getId(),body.subscriptionId())));}
    @GetMapping("/preferences") public PushPreferenceDto preference(Principal actor){return reminders.preference(users.userId(actor.getName()));}
    @PutMapping("/preferences") public PushPreferenceDto preference(Principal actor,@Valid @RequestBody PushPreferenceRequestDto body){return reminders.preference(users.userId(actor.getName()),body.dailyReminders());}
}
