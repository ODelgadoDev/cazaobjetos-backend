package mx.utch.cazaobjetos.controller;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import mx.utch.cazaobjetos.dto.PhotoResponseDto;
import mx.utch.cazaobjetos.service.*;
@RestController
@RequestMapping("${url.api}/found-items/{parent}/photos")
public class FoundPhotoController {
    private final PhotoService photos;
    private final UserDataService users;
    public FoundPhotoController(PhotoService photos,UserDataService users) { this.photos=photos;this.users=users; }
    @GetMapping
    public List<PhotoResponseDto> list(@PathVariable UUID parent,Principal principal) { return photos.list(true,parent,users.userId(principal.getName())); }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoResponseDto> upload(@PathVariable UUID parent,Principal principal,@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(201).body(photos.upload(true,parent,users.userId(principal.getName()),file));
    }
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> content(@PathVariable UUID parent,@PathVariable UUID id,Principal principal) {
        var photo=photos.content(true,parent,id,users.userId(principal.getName()));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.getContentType()))
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .header("Content-Disposition","inline; filename=\""+id+(photo.getContentType().equals("image/png")?".png":".jpg")+"\"")
            .body(photo.getContent());
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID parent,@PathVariable UUID id,Principal principal) {
        photos.delete(true,parent,id,users.userId(principal.getName()));return ResponseEntity.noContent().build();
    }
}
