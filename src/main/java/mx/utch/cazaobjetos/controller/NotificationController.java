package mx.utch.cazaobjetos.controller;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.NotificationFilter;
import mx.utch.cazaobjetos.service.*;
@RestController
@RequestMapping("${url.api}/notifications")
public class NotificationController {
    private final NotificationService notifications;
    private final UserDataService users;
    public NotificationController(NotificationService notifications,UserDataService users) {this.notifications=notifications;this.users=users;}
    @GetMapping
    public PageResponseDto<NotificationResponseDto> list(Principal principal,@RequestParam(defaultValue="ALL") NotificationFilter filter,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize) {return notifications.list(users.userId(principal.getName()),filter,page,pageSize);}
    @GetMapping("/summary")
    public NotificationSummaryDto summary(Principal principal) {return notifications.summary(users.userId(principal.getName()));}
    @PostMapping("/{id}/read")
    public NotificationResponseDto read(Principal principal,@PathVariable UUID id) {return notifications.read(users.userId(principal.getName()),id);}
    @PostMapping("/clear-read")
    public NotificationClearDto clear(Principal principal) {return notifications.clearRead(users.userId(principal.getName()));}
}
