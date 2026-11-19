package mx.utch.cazaobjetos.controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.service.*;
import mx.utch.cazaobjetos.util.EvidenceImageUtil;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("${url.api}/lost-reports/{lossId}/responses")
public class LostResponseController {
    private final LostResponseService responses;
    private final UserDataService users;
    private final PhotoImportService imports;

    public LostResponseController(LostResponseService responses, UserDataService users, PhotoImportService imports) {
        this.responses=responses;
        this.users=users;
        this.imports=imports;
    }

    @GetMapping
    public List<LostResponseDto> list(Principal principal,@PathVariable UUID lossId) {
        return responses.list(lossId,actor(principal));
    }

    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LostResponseDto create(Principal principal,@PathVariable UUID lossId,
            @Valid @RequestPart("request") LostResponseRequestDto request,
            @RequestPart(value="file",required=false) MultipartFile file,
            @RequestHeader(value="Idempotency-Key",required=false) String key) {
        UUID actor=actor(principal);
        var image=imports.attachment(file,request.photoUrl());
        return responses.create(lossId,actor,key,request,image==null?null:EvidenceImageUtil.normalize(image));
    }

    @GetMapping("/{responseId}/comments")
    public List<LostResponseCommentDto> comments(Principal principal,@PathVariable UUID lossId,@PathVariable UUID responseId) {
        return responses.comments(lossId,responseId,actor(principal));
    }

    @PostMapping("/{responseId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public LostResponseCommentDto comment(Principal principal,@PathVariable UUID lossId,@PathVariable UUID responseId,
            @Valid @RequestBody ClaimCommentRequestDto request,
            @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return responses.comment(lossId,responseId,actor(principal),key,request.body());
    }

    @PostMapping("/{responseId}/complete")
    public LostResponseDto complete(Principal principal,@PathVariable UUID lossId,@PathVariable UUID responseId) {
        return responses.decide(lossId,responseId,actor(principal),true);
    }

    @PostMapping("/{responseId}/cancel")
    public LostResponseDto cancel(Principal principal,@PathVariable UUID lossId,@PathVariable UUID responseId) {
        return responses.decide(lossId,responseId,actor(principal),false);
    }

    @GetMapping("/{responseId}/photo")
    public ResponseEntity<byte[]> photo(Principal principal,@PathVariable UUID lossId,@PathVariable UUID responseId) {
        var image=responses.photo(lossId,responseId,actor(principal));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(image.content());
    }

    private UUID actor(Principal principal) { return users.userId(principal.getName()); }
}
