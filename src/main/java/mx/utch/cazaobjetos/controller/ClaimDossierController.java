package mx.utch.cazaobjetos.controller;

import java.security.Principal;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.service.*;

@RestController
@RequestMapping("${url.api}/claims/{claimId}")
public class ClaimDossierController {
    private final ClaimDossierService dossier;
    private final UserDataService users;
    private final PhotoImportService imports;
    public ClaimDossierController(ClaimDossierService dossier,UserDataService users,PhotoImportService imports) {
        this.dossier=dossier;this.users=users;this.imports=imports;
    }
    @PostMapping(value="/evidence/import",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ClaimEvidenceDto> link(Principal principal,@PathVariable UUID claimId,
            @Valid @RequestBody ImportPhotoRequestDto request,@RequestHeader(value="Idempotency-Key",required=false) String key) {
        UUID actor=users.userId(principal.getName());
        dossier.evidence(actor,claimId);
        return ResponseEntity.status(201).body(dossier.upload(actor,claimId,imports.link(request.url()),key,true));
    }
    @GetMapping("/authors/{authorId}/photo")
    public ResponseEntity<byte[]> avatar(Principal principal,@PathVariable UUID claimId,@PathVariable UUID authorId) {
        var photo=dossier.avatar(users.userId(principal.getName()),claimId,authorId);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").body(photo.getContent());
    }
    @GetMapping("/activity")
    public PageResponseDto<ClaimActivityDto> activity(Principal principal,@PathVariable UUID claimId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize) {
        return dossier.activity(users.userId(principal.getName()),claimId,page,pageSize);
    }
    @PostMapping("/comments")
    public ResponseEntity<ClaimActivityDto> comment(Principal principal,@PathVariable UUID claimId,@Valid @RequestBody ClaimCommentRequestDto request,@RequestHeader(value="Idempotency-Key",required=false) String key) {
        return ResponseEntity.status(201).body(dossier.comment(users.userId(principal.getName()),claimId,request,key));
    }
    @GetMapping("/evidence")
    public List<ClaimEvidenceDto> evidence(Principal principal,@PathVariable UUID claimId) { return dossier.evidence(users.userId(principal.getName()),claimId); }
    @PostMapping(value="/evidence",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClaimEvidenceDto> upload(Principal principal,@PathVariable UUID claimId,@RequestParam("file") MultipartFile file,@RequestHeader(value="Idempotency-Key",required=false) String key) {
        return ResponseEntity.status(201).body(dossier.upload(users.userId(principal.getName()),claimId,file,key));
    }
    @GetMapping("/evidence/{evidenceId}")
    public ResponseEntity<byte[]> content(Principal principal,@PathVariable UUID claimId,@PathVariable UUID evidenceId) {
        var image=dossier.content(users.userId(principal.getName()),claimId,evidenceId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.getContentType())).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline; filename=\"evidence"+(image.getContentType().equals("image/png")?".png":".jpg")+"\"").body(image.getContent());
    }
}
