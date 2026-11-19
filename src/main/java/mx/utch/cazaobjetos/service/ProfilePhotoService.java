package mx.utch.cazaobjetos.service;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import mx.utch.cazaobjetos.exception.BusinessException;
import mx.utch.cazaobjetos.model.ProfilePhotoModel;
import mx.utch.cazaobjetos.repository.ProfilePhotoRepository;
import mx.utch.cazaobjetos.util.AvatarImageUtil;

@Service
@Transactional
public class ProfilePhotoService {
    private final ProfilePhotoRepository photos;
    private final UserDataService users;
    private final ItemEventService events;
    public ProfilePhotoService(ProfilePhotoRepository photos, UserDataService users, ItemEventService events) {
        this.photos = photos; this.users = users; this.events = events;
    }

    @Transactional(readOnly = true)
    public ProfilePhotoModel content(String email) {
        return content(users.userId(email));
    }
    @Transactional(readOnly = true)
    public ProfilePhotoModel content(UUID userId) {
        return photos.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "No hay foto de perfil"));
    }
    public void replace(String email, MultipartFile file) {
        var user = users.lockMe(email);
        byte[] content = AvatarImageUtil.normalize(file);
        var photo = photos.findById(user.getId()).orElseGet(ProfilePhotoModel::new);
        photo.setUserId(user.getId()); photo.setVersion(UUID.randomUUID());
        photo.setContentType("image/jpeg"); photo.setContent(content);
        photo.setSizeBytes(content.length); photo.setUpdatedAt(Instant.now());
        photos.save(photo);
        events.audit(user.getId(), user.getId(), "PROFILE_PHOTO_UPDATED");
    }
    public void delete(String email) {
        var user = users.lockMe(email);
        photos.findById(user.getId()).ifPresent(photo -> {
            photos.delete(photo);
            events.audit(user.getId(), user.getId(), "PROFILE_PHOTO_DELETED");
        });
    }
}
