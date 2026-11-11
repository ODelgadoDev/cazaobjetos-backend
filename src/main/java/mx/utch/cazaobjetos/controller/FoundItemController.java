package mx.utch.cazaobjetos.controller;

import java.security.Principal;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.service.*;

@RestController
@RequestMapping("${url.api}/found-items")
public class FoundItemController {
    private final ItemService service;
    private final CreationService creations;
    private final CatalogService catalog;
    private final UserDataService users;

    public FoundItemController(ItemService service, UserDataService users, CatalogService catalog, CreationService creations) {
        this.creations=creations;
        this.service = service;
        this.catalog = catalog;
        this.users = users;
    }

    @GetMapping
    public ResponseEntity<PageResponseDto<FoundItemResponseDto>> findAll(@Valid @ModelAttribute CatalogQueryDto query) {
        return ResponseEntity.ok(catalog.found(query));
    }
    @GetMapping("/{id}")
    public ResponseEntity<FoundItemResponseDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.found(id));
    }
    @GetMapping("/mine")
    public ResponseEntity<PageResponseDto<FoundItemResponseDto>> mine(Principal principal, @Valid @ModelAttribute CatalogQueryDto query) {
        return ResponseEntity.ok(catalog.myFoundItems(users.userId(principal.getName()), query));
    }
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(Principal principal, @PathVariable UUID id, @Valid @RequestBody ReportRequestDto request) {
        service.editFound(users.userId(principal.getName()), id, request);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<Void> withdraw(Principal principal, @PathVariable UUID id) {
        service.withdrawFound(users.userId(principal.getName()), id);
        return ResponseEntity.noContent().build();
    }
    @PostMapping
    public ResponseEntity<IdResponseDto> create(@RequestHeader(value="Idempotency-Key",required=false) String key, Principal principal, @Valid @RequestBody ReportRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponseDto(creations.found(users.userId(principal.getName()), key, request)));
    }
    @PostMapping("/{id}/office")
    public ResponseEntity<Void> office(Principal principal, @PathVariable UUID id) {
        service.office(users.userId(principal.getName()), id);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/delegate")
    public ResponseEntity<Void> delegate(Principal principal, @PathVariable UUID id, @Valid @RequestBody DelegateRequestDto request) {
        service.delegate(users.userId(principal.getName()), id, request.responsibleId());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/claims")
    public ResponseEntity<IdResponseDto> claim(@RequestHeader(value="Idempotency-Key",required=false) String key, Principal principal, @PathVariable UUID id, @Valid @RequestBody ClaimRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponseDto(creations.claim(users.userId(principal.getName()), id, key, request)));
    }
}
