package mx.utch.cazaobjetos.controller;

import java.security.Principal;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.service.*;

@RestController
@RequestMapping("${url.api}/lost-reports")
public class LostReportController {
    private final FoundPublicationService publications;
    private final ItemService service;
    private final CreationService creations;
    private final CatalogService catalog;
    private final UserDataService users;

    public LostReportController(ItemService service, UserDataService users, CatalogService catalog, CreationService creations, FoundPublicationService publications) {
        this.creations=creations;this.publications=publications;
        this.service = service;
        this.catalog = catalog;
        this.users = users;
    }

    @GetMapping
    public ResponseEntity<PageResponseDto<LostReportResponseDto>> findAll(@Valid @ModelAttribute CatalogQueryDto query) {
        return ResponseEntity.ok(catalog.lost(query));
    }
    @GetMapping("/{id}")
    public ResponseEntity<LostReportResponseDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.lost(id));
    }
    @GetMapping("/{id}/matches")
    public PageResponseDto<FoundItemResponseDto> matches(Principal principal,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize) {
        return publications.matches(users.userId(principal.getName()),id,page,pageSize);
    }
    @GetMapping("/mine")
    public ResponseEntity<PageResponseDto<LostReportResponseDto>> mine(Principal principal, @Valid @ModelAttribute CatalogQueryDto query) {
        return ResponseEntity.ok(catalog.myLostReports(users.userId(principal.getName()), query));
    }
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(Principal principal, @PathVariable UUID id, @Valid @RequestBody ReportRequestDto request) {
        service.editLost(users.userId(principal.getName()), id, request);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<Void> withdraw(Principal principal, @PathVariable UUID id) {
        service.withdrawLost(users.userId(principal.getName()), id);
        return ResponseEntity.noContent().build();
    }
    @PostMapping
    public ResponseEntity<IdResponseDto> create(@RequestHeader(value="Idempotency-Key",required=false) String key, Principal principal, @Valid @RequestBody ReportRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponseDto(creations.lost(users.userId(principal.getName()), key, request)));
    }
    @PostMapping("/{id}/recover")
    public ResponseEntity<Void> recover(Principal principal, @PathVariable UUID id) {
        service.recoverLost(users.userId(principal.getName()), id);
        return ResponseEntity.noContent().build();
    }
}
