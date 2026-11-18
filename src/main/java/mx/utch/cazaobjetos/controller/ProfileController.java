package mx.utch.cazaobjetos.controller;

import java.security.Principal;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.service.*;

@RestController
@RequestMapping("${url.api}/auth/me")
public class ProfileController {
    private final ProfileService profile;
    private final ProfilePhotoService photos;
    private final UserDataService users;
    public ProfileController(ProfileService profile, ProfilePhotoService photos, UserDataService users) {
        this.profile = profile; this.photos = photos; this.users = users;
    }

    @PutMapping
    public UserResponseDto update(Principal principal, @Valid @RequestBody UpdateProfileRequestDto request) {
        return profile.update(principal.getName(), request);
    }
    @PutMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponseDto upload(Principal principal, @RequestParam("file") MultipartFile file) {
        photos.replace(principal.getName(), file);
        return users.me(principal.getName());
    }
    @DeleteMapping("/photo")
    public UserResponseDto delete(Principal principal) {
        photos.delete(principal.getName());
        return users.me(principal.getName());
    }
    @GetMapping("/photo")
    public ResponseEntity<byte[]> content(Principal principal) {
        var photo = photos.content(principal.getName());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline; filename=\"avatar.jpg\"")
                .body(photo.getContent());
    }
}
