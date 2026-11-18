package mx.utch.cazaobjetos.service;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import mx.utch.cazaobjetos.constants.Constants;
import mx.utch.cazaobjetos.dto.UserResponseDto;
import mx.utch.cazaobjetos.exception.BusinessException;
import mx.utch.cazaobjetos.model.UserModel;
import mx.utch.cazaobjetos.repository.UserRepository;
import mx.utch.cazaobjetos.repository.ProfilePhotoRepository;

@Service
@Transactional(readOnly = true)
public class UserDataService {
    private final UserRepository users;
    private final ProfilePhotoRepository photos;
    public UserDataService(UserRepository users, ProfilePhotoRepository photos) { this.users = users; this.photos = photos; }
    public UserModel user(UUID id) {
        return users.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, Constants.NOT_FOUND));
    }
    public UserModel enabledUser(UUID id) {
        UserModel user = user(id);
        if (!user.getEnabled()) throw new BusinessException(HttpStatus.NOT_FOUND, Constants.NOT_FOUND);
        return user;
    }
    public UUID userId(String email) {
        return users.findByEmailAndEnabledTrue(email)
            .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Cuenta no disponible")).getId();
    }
    @Transactional
    public UserModel lockMe(String email) {
        var user = users.findLockedById(userId(email))
            .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Cuenta no disponible"));
        if (!user.getEnabled()) throw new BusinessException(HttpStatus.UNAUTHORIZED, "Cuenta no disponible");
        return user;
    }
    public UserResponseDto me(String email) {
        var user = users.findByEmailAndEnabledTrue(email)
            .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Cuenta no disponible"));
        return user.toResponseDto(photos.findVersionByUserId(user.getId()).orElse(null));
    }
}
