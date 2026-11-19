package mx.utch.cazaobjetos.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.model.*;
import mx.utch.cazaobjetos.repository.*;
import mx.utch.cazaobjetos.util.EvidenceImageUtil;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;

@Service
@Transactional
public class ClaimDossierService {
    public static final int MAX_EVIDENCE=5;
    private final ItemDataService items;
    private final ClaimActivityRepository activities;
    private final ClaimEvidenceRepository evidence;
    private final ClaimJournalService journal;
    private final ItemEventService audit;
    public ClaimDossierService(ItemDataService items,ClaimActivityRepository activities,ClaimEvidenceRepository evidence,ClaimJournalService journal,ItemEventService audit) {
        this.items=items;this.activities=activities;this.evidence=evidence;this.journal=journal;this.audit=audit;
    }
    private ClaimModel access(UUID actor,UUID id,boolean write) {
        // Same lock order as decisions: found item, then claim. Closure and contributions serialize.
        UUID parent=items.itemIdForClaim(id);
        var item=write?items.lockItem(parent):items.found(parent);
        var claim=items.claim(id);
        require(actor.equals(claim.getClaimantId())||actor.equals(item.getResponsibleId()),HttpStatus.NOT_FOUND,"Expediente no disponible");
        if(write) require(item.getStatus()==FoundStatus.AVAILABLE&&Set.of(ClaimStatus.PENDING,ClaimStatus.ACCEPTED).contains(claim.getStatus()),HttpStatus.CONFLICT,"El reclamo está cerrado; su expediente es de solo lectura");
        return claim;
    }
    @Transactional(readOnly=true)
    public PageResponseDto<ClaimActivityDto> activity(UUID actor,UUID id,int page,int size) {
        access(actor,id,false);require(page>=0&&page<=10000&&size>=1&&size<=100,HttpStatus.BAD_REQUEST,"Paginación inválida");
        return PageResponseDto.from(activities.findByClaimId(id,PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("sequence")))).map(ClaimJournalService::response));
    }
    @Transactional(readOnly=true)
    public List<ClaimEvidenceDto> evidence(UUID actor,UUID id) { access(actor,id,false);return evidence.list(id); }
    @Transactional(readOnly=true)
    public ClaimEvidenceModel content(UUID actor,UUID claimId,UUID evidenceId) {
        access(actor,claimId,false);
        var image=evidence.findById(evidenceId).orElse(null);
        require(image!=null&&image.getClaimId().equals(claimId),HttpStatus.NOT_FOUND,"Evidencia no disponible");return image;
    }
    private Optional<ClaimActivityModel> existing(UUID actor,UUID claim,ClaimActivityKind kind,String key,String hash) {
        if(key==null)return Optional.empty();
        require(key.matches("[A-Za-z0-9._:-]{8,100}"),HttpStatus.BAD_REQUEST,"Idempotency-Key requiere 8 a 100 caracteres válidos");
        var saved=activities.findByClaimIdAndActorIdAndKindAndRequestKey(claim,actor,kind,key);
        if(saved.isPresent())require(hash.equals(saved.get().getRequestHash()),HttpStatus.CONFLICT,"La clave de reintento ya se usó con otro contenido");
        return saved;
    }
    public ClaimActivityDto comment(UUID actor,UUID id,ClaimCommentRequestDto request,String key) {
        access(actor,id,true);
        String body=request.body()==null?"":request.body().strip();
        require(!body.isBlank()&&body.length()<=2000,HttpStatus.BAD_REQUEST,"Escribe un comentario de 1 a 2000 caracteres");
        require(body.codePoints().noneMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'),HttpStatus.BAD_REQUEST,"El comentario contiene caracteres inválidos");
        String hash=EvidenceImageUtil.hash(body.getBytes(StandardCharsets.UTF_8));
        var prior=existing(actor,id,ClaimActivityKind.COMMENT,key,hash);
        if(prior.isPresent())return ClaimJournalService.response(prior.get());
        var entry=journal.append(id,actor,ClaimActivityKind.COMMENT,body,null,null,key,key==null?null:hash);
        audit.audit(actor,id,"CLAIM_COMMENT_ADDED");return ClaimJournalService.response(entry);
    }
    public ClaimEvidenceDto upload(UUID actor,UUID id,MultipartFile file,String key) {
        return upload(actor,id,file,key,false);
    }
    public ClaimEvidenceDto upload(UUID actor,UUID id,MultipartFile file,String key,boolean reference) {
        access(actor,id,true);
        var image=EvidenceImageUtil.normalize(file);
        var prior=existing(actor,id,ClaimActivityKind.EVIDENCE_ADDED,key,evidenceHash(image,reference));
        if(prior.isPresent()) {
            var saved=evidence.findById(prior.get().getEvidenceId()).orElseThrow();return response(saved);
        }
        return addPrepared(actor,id,image,key,reference);
    }
    public void attachInitial(UUID actor,UUID id,List<EvidenceImageUtil.Image> images) {
        attachInitial(actor,id,images,List.of());
    }
    public void attachInitial(UUID actor,UUID id,List<EvidenceImageUtil.Image> images,List<EvidenceImageUtil.Image> references) {
        access(actor,id,true);
        require(images.size()+references.size()<=MAX_EVIDENCE,HttpStatus.BAD_REQUEST,"Máximo 5 imágenes por reclamo");
        images.forEach(image->addPrepared(actor,id,image,null,false));
        references.forEach(image->addPrepared(actor,id,image,null,true));
    }
    private static String evidenceHash(EvidenceImageUtil.Image image,boolean reference) {
        return reference?EvidenceImageUtil.hash(("reference:"+image.hash()).getBytes(StandardCharsets.UTF_8)):image.hash();
    }
    private ClaimEvidenceDto addPrepared(UUID actor,UUID id,EvidenceImageUtil.Image image,String key,boolean reference) {
        require(evidence.countByClaimId(id)<MAX_EVIDENCE,HttpStatus.CONFLICT,"El reclamo ya tiene el máximo de 5 evidencias; las enviadas se conservan en el historial");
        var photo=new ClaimEvidenceModel();photo.setId(UUID.randomUUID());photo.setClaimId(id);photo.setAuthorId(actor);
        photo.setContentType(image.type());photo.setContent(image.bytes());photo.setSizeBytes(image.bytes().length);photo.setCreatedAt(Instant.now());
        photo.setReferenceImage(reference);
        evidence.save(photo);
        journal.append(id,actor,ClaimActivityKind.EVIDENCE_ADDED,"Se adjuntó una imagen de evidencia.",null,photo.getId(),key,key==null?null:evidenceHash(image,reference));
        audit.audit(actor,id,"CLAIM_EVIDENCE_ADDED");return response(photo);
    }
    private static ClaimEvidenceDto response(ClaimEvidenceModel p) { return new ClaimEvidenceDto(p.getId(),p.getAuthorId(),p.getContentType(),p.getSizeBytes(),p.getCreatedAt(),p.isReferenceImage()); }
}
