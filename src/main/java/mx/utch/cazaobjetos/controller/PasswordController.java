package mx.utch.cazaobjetos.controller;
import java.security.Principal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.service.*;
@RestController
@RequestMapping("${url.api}/auth")
public class PasswordController {
    private final PasswordService passwords;
    private final UserDataService users;
    public PasswordController(PasswordService passwords,UserDataService users) { this.passwords=passwords;this.users=users; }
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgot(@Valid @RequestBody EmailRequestDto request) { passwords.forgot(request.email());return ResponseEntity.accepted().build(); }
    @PostMapping("/reset-password")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequestDto request) { passwords.reset(request.token(),request.newPassword());return ResponseEntity.noContent().build(); }
    @PostMapping("/change-password")
    public ResponseEntity<Void> change(Principal principal,@Valid @RequestBody ChangePasswordRequestDto request) {
        passwords.change(users.userId(principal.getName()),request.currentPassword(),request.newPassword());return ResponseEntity.noContent().build();
    }
}
