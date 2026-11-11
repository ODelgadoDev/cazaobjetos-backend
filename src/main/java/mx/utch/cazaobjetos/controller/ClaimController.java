package mx.utch.cazaobjetos.controller;

import java.security.Principal;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.service.*;

@RestController
@RequestMapping("${url.api}/claims")
public class ClaimController {
    private final ItemService service;
    private final ClaimQueryService queries;
    private final UserDataService users;

    public ClaimController(ItemService service, UserDataService users, ClaimQueryService queries) {
        this.queries=queries;
        this.service = service;
        this.users = users;
    }

    @GetMapping
    public ResponseEntity<List<ClaimResponseDto>> findAll(Principal principal, @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(service.claims(users.userId(principal.getName()), page));
    }
    @GetMapping("/search")
    public PageResponseDto<ClaimResponseDto> search(Principal principal,@Valid @ModelAttribute ClaimQueryDto query) {
        return queries.search(users.userId(principal.getName()),query);
    }
    @GetMapping("/{id}")
    public ClaimDetailDto detail(Principal principal,@PathVariable UUID id) {
        return queries.detail(users.userId(principal.getName()),id);
    }
    @PostMapping("/{id}/{action}")
    public ResponseEntity<Void> decide(Principal principal, @PathVariable UUID id, @PathVariable String action) {
        service.decide(users.userId(principal.getName()), id, action);
        return ResponseEntity.noContent().build();
    }
}
