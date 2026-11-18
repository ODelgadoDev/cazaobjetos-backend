package mx.utch.cazaobjetos.service;
import java.util.*;
import java.util.function.Supplier;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import mx.utch.cazaobjetos.dto.*;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;
@Service
@Transactional
public class CreationService {
    private final ItemService items;
    private final CreationDataService data;
    private final ObjectMapper json;
    private final ClaimDossierService dossier;
    private final FoundPublicationService publications;
    public CreationService(ItemService items,CreationDataService data,ObjectMapper json,ClaimDossierService dossier,FoundPublicationService publications) { this.items=items;this.data=data;this.json=json;this.dossier=dossier;this.publications=publications; }
    private byte[] hash(String input) {
        try { return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private UUID run(UUID actor,String scope,String key,Object request,Supplier<UUID> create) {
        if(key==null) return create.get();
        require(key.matches("[A-Za-z0-9._:-]{8,100}"),HttpStatus.BAD_REQUEST,"Idempotency-Key requiere 8 a 100 caracteres alfanuméricos o . _ : -");
        String fingerprint=HexFormat.of().formatHex(hash(json.writeValueAsString(request)));
        data.lock(ByteBuffer.wrap(hash(actor+"|"+scope+"|"+key)).getLong());
        var existing=data.find(actor,scope,key);
        if(existing.isPresent()) {
            require(existing.get().hash().equals(fingerprint),HttpStatus.CONFLICT,"La clave de idempotencia ya se usó con otros datos");
            return existing.get().id();
        }
        UUID id=create.get();data.save(actor,scope,key,fingerprint,id);return id;
    }
    public UUID found(UUID actor,String key,ReportRequestDto request) { return run(actor,"found",key,request,()->items.createFound(actor,request)); }
    public UUID publishFound(UUID actor,String key,FoundPublicationRequestDto request,org.springframework.web.multipart.MultipartFile file) {
        require(file!=null&&!file.isEmpty(),HttpStatus.BAD_REQUEST,"Adjunta una foto real del objeto encontrado");
        require(file.getSize()<=5*1024*1024,HttpStatus.PAYLOAD_TOO_LARGE,"La foto excede 5 MB");
        String imageHash;
        try {imageHash=mx.utch.cazaobjetos.util.EvidenceImageUtil.hash(file.getBytes());}
        catch(java.io.IOException error) {throw new mx.utch.cazaobjetos.exception.BusinessException(HttpStatus.BAD_REQUEST,"No se pudo leer la foto");}
        return run(actor,"found-publish",key,Map.of("request",request,"image",imageHash),()->publications.publish(actor,request,file));
    }
    public UUID lost(UUID actor,String key,ReportRequestDto request) { return run(actor,"lost",key,request,()->items.createLost(actor,request)); }
    public UUID claimWithEvidence(UUID actor,UUID parent,String key,ClaimRequestDto request,List<org.springframework.web.multipart.MultipartFile> files) {
        require(files.size()<=ClaimDossierService.MAX_EVIDENCE,HttpStatus.BAD_REQUEST,"Máximo 5 imágenes por reclamo");
        var images=files.stream().map(mx.utch.cazaobjetos.util.EvidenceImageUtil::normalize).toList();
        Object payload=images.isEmpty()?request:Map.of("claim",request,"evidence",images.stream().map(mx.utch.cazaobjetos.util.EvidenceImageUtil.Image::hash).toList());
        return run(actor,"claim:"+parent,key,payload,()->{UUID id=items.claim(actor,parent,request);dossier.attachInitial(actor,id,images);return id;});
    }
    public UUID claim(UUID actor,UUID parent,String key,ClaimRequestDto request) { return run(actor,"claim:"+parent,key,request,()->items.claim(actor,parent,request)); }
}
