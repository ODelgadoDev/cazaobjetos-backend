package mx.utch.cazaobjetos.controller;

import java.security.Principal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.service.UserDataService;

@RestController
@RequestMapping("${url.api}/auth")
public class AuthController {
    private final UserDataService users;
    public AuthController(UserDataService users) { this.users = users; }

    @GetMapping("/csrf")
    public CsrfResponseDto csrf(CsrfToken token) {
        return new CsrfResponseDto(token.getHeaderName(), token.getToken());
    }
    @GetMapping("/me")
    public UserResponseDto me(Principal principal) { return users.me(principal.getName()); }
}
