package mx.utch.cazaobjetos.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.service.AccountService;

@RestController
@RequestMapping("${url.api}/auth")
public class RegistrationController {
    private final AccountService accounts;
    public RegistrationController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping("/registration-options")
    public RegistrationOptionsDto options() { return new RegistrationOptionsDto(accounts.demoVerification(), accounts.openRegistration()); }

    @PostMapping("/verify-email/demo")
    public ResponseEntity<Void> verifyDemo(@Valid @RequestBody EmailRequestDto request) {
        accounts.verifyDemo(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequestDto request) {
        accounts.register(request);
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resend(@Valid @RequestBody EmailRequestDto request) {
        accounts.resend(request.email());
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verify(@Valid @RequestBody VerifyEmailRequestDto request) {
        accounts.verify(request.token());
        return ResponseEntity.noContent().build();
    }
}
