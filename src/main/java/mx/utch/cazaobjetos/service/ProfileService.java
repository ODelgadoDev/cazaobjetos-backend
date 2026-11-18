package mx.utch.cazaobjetos.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import mx.utch.cazaobjetos.dto.*;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;

@Service
@Transactional
public class ProfileService {
    private final UserDataService users;
    private final ItemEventService events;
    public ProfileService(UserDataService users, ItemEventService events) {
        this.users = users; this.events = events;
    }

    public UserResponseDto update(String email, UpdateProfileRequestDto request) {
        var user = users.lockMe(email);
        String name = clean(request.displayName());
        require(name != null && name.length() >= 3, HttpStatus.BAD_REQUEST, "Escribe un nombre de al menos 3 caracteres");
        user.setDisplayName(name);
        user.setAlias(clean(request.alias()));
        user.setClassroom(clean(request.classroom()));
        user.setGroupName(clean(request.groupName()));
        user.setCourse(clean(request.course()));
        user.setDegree(clean(request.degree()));
        user.setBuilding(clean(request.building()));
        events.audit(user.getId(), user.getId(), "PROFILE_UPDATED");
        return users.me(email);
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.strip();
        require(result.codePoints().noneMatch(Character::isISOControl), HttpStatus.BAD_REQUEST, "Los datos del perfil no admiten saltos de línea ni caracteres de control");
        return result;
    }
}
