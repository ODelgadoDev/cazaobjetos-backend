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

    private final PhotoImportService imports;
    public FoundItemController(ItemService service, UserDataService users, CatalogService catalog, CreationService creations, PhotoImportService imports) {
        this.imports=imports;
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
    @PostMapping(value="/publish",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IdResponseDto> publish(@RequestHeader(value="Idempotency-Key",required=false) String key,Principal principal,
            @Valid @RequestPart("request") FoundPublicationRequestDto request,@RequestPart(value="file",required=false) org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.status(201).body(new IdResponseDto(creations.publishFound(users.userId(principal.getName()),key,request,imports.attachment(file,request.photoUrl()))));
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
    @PostMapping(value="/{id}/claims",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IdResponseDto> claim(@RequestHeader(value="Idempotency-Key",required=false) String key, Principal principal, @PathVariable UUID id, @Valid @RequestBody ClaimRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponseDto(creations.claim(users.userId(principal.getName()), id, key, request)));
    }
    @PostMapping(value="/{id}/claims",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IdResponseDto> claimWithEvidence(@RequestHeader(value="Idempotency-Key",required=false) String key,Principal principal,@PathVariable UUID id,
            @Valid @RequestPart("request") ClaimRequestDto request,@RequestPart(value="files",required=false) List<org.springframework.web.multipart.MultipartFile> files,
            @RequestPart(value="urls",required=false) List<String> urls) {
        var local=files==null?List.<org.springframework.web.multipart.MultipartFile>of():files;
        var links=urls==null?List.<String>of():urls;
        mx.utch.cazaobjetos.util.ValidateUtil.require(local.size()+links.size()<=ClaimDossierService.MAX_EVIDENCE,
            HttpStatus.BAD_REQUEST,"Máximo 5 imágenes por reclamo");
        var remote=links.stream().map(url->imports.link(url)).toList();
        return ResponseEntity.status(201).body(new IdResponseDto(creations.claimWithEvidence(users.userId(principal.getName()),id,key,request,local,remote)));
    }
    @PutMapping("/{id}/custody")
    public ResponseEntity<Void> custody(Principal principal,@PathVariable UUID id,@Valid @RequestBody CustodyRequestDto request) {
        service.custody(users.userId(principal.getName()),id,request);return ResponseEntity.noContent().build();
    }

}
