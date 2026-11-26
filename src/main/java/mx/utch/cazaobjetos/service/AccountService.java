package mx.utch.cazaobjetos.service;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import mx.utch.cazaobjetos.dto.RegisterRequestDto;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.exception.BusinessException;
import mx.utch.cazaobjetos.model.*;
import mx.utch.cazaobjetos.repository.*;
import mx.utch.cazaobjetos.util.PasswordPolicy;
import mx.utch.cazaobjetos.util.VerificationTokenUtil;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;

@Service
@Transactional
public class AccountService {
    private final RegistrationMode mode;
    private final UserRepository users;
    private final EmailVerificationRepository verifications;
    private final EmailOutboxRepository outbox;
    private final PasswordEncoder passwords;

    public AccountService(UserRepository users, EmailVerificationRepository verifications,
                          EmailOutboxRepository outbox, PasswordEncoder passwords, RegistrationMode mode) {
        this.mode=mode;
        this.users = users;
        this.verifications = verifications;
        this.outbox = outbox;
        this.passwords = passwords;
    }

    public boolean demoVerification() { return mode.demoVerification(); }

    public void verifyDemo(String rawEmail) {
        require(mode.demoVerification(),HttpStatus.NOT_FOUND,"Verificación de prueba no disponible");
        UUID id=users.findIdByEmail(normalize(rawEmail)).orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"Crea tu cuenta primero"));
        UserModel user=users.findLockedById(id).orElseThrow(this::invalidToken);
        if(user.getEnabled() && user.getEmailVerifiedAt()!=null) return;
        require(user.getEmailVerifiedAt()==null,HttpStatus.CONFLICT,"Esta cuenta está deshabilitada");
        var verification=verifications.findById(id).orElseThrow(this::invalidToken);
        require(verification.getConsumedAt()==null,HttpStatus.CONFLICT,"Esta cuenta no tiene una activación pendiente");
        Instant now=Instant.now();
        user.setEmailVerifiedAt(now);
        user.setEnabled(true);
        verification.setConsumedAt(now);
    }

    private String normalize(String raw) {
        String email = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        require(email.length() <= 254 && email.matches("[^@\\s]+@[^@\\s]+"),
                HttpStatus.BAD_REQUEST, "Escribe un correo válido");
        return email;
    }

    public void register(RegisterRequestDto request) {
        String email = normalize(request.email());
        String degree=academicField(request.degree(),160,"carrera o área");
        String building=request.building()==null || request.building().isBlank()
                ? null : academicField(request.building(),80,"edificio");
        PasswordPolicy.validate(request.password());
        require(!users.existsByEmail(email), HttpStatus.CONFLICT, "Este correo ya está registrado. Inicia sesión o verifica tu cuenta.");
        UserModel user = new UserModel();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setDisplayName(request.displayName().strip());
        user.setDegree(degree);
        user.setBuilding(building);
        user.setPasswordHash(passwords.encode(request.password()));
        user.setRole(UserRole.STUDENT); // Public registration cannot grant teacher/secretary privileges.
        user.setEnabled(false);
        user.setCreatedAt(Instant.now());
        users.saveAndFlush(user);
        issue(user);
    }

    private String academicField(String raw, int max, String label) {
        require(raw!=null && !raw.isBlank(),HttpStatus.BAD_REQUEST,"Escribe tu "+label);
        String value=raw.strip();
        require(value.length()<=max && value.codePoints().noneMatch(Character::isISOControl),
                HttpStatus.BAD_REQUEST,"Revisa tu "+label+": debe escribirse en una sola línea");
        return value;
    }

    public void resend(String rawEmail) {
        String email = normalize(rawEmail);
        UUID id = users.findIdByEmail(email).orElse(null);
        if (id == null) return; // Same HTTP response for absent or already verified accounts.
        UserModel user = users.findLockedById(id).orElseThrow();
        if (user.getEnabled() || user.getEmailVerifiedAt() != null) return;
        var current = verifications.findById(id).orElse(null);
        if (current != null && current.getSentAt().plusSeconds(60).isAfter(Instant.now())) return;
        issue(user);
    }

    private void issue(UserModel user) {
        Instant now = Instant.now();
        String token = VerificationTokenUtil.generate();
        EmailVerificationModel verification = verifications.findById(user.getId()).orElseGet(EmailVerificationModel::new);
        verification.setUserId(user.getId());
        verification.setTokenHash(VerificationTokenUtil.hash(token));
        verification.setExpiresAt(now.plus(Duration.ofMinutes(30)));
        verification.setSentAt(now);
        verification.setConsumedAt(null);
        verifications.save(verification);
        if(mode.demoVerification()) return;
        EmailOutboxModel email = new EmailOutboxModel();
        email.setId(UUID.randomUUID());
        email.setEventKey("VERIFY:" + UUID.randomUUID());
        email.setRecipient(user.getEmail());
        email.setSubject("Verifica tu correo en CazaObjetos UTCH");
        email.setBody("Se solicitó una cuenta de CazaObjetos con este correo. Si no fuiste tú, ignora este mensaje.\n\n"
                + "Código de verificación (válido durante 30 minutos):\n" + token
                + "\n\nEn la app o Postman, envía este código a POST /api/v1/auth/verify-email."
                + "\nSolo el código más reciente es válido.");
        email.setStatus(EmailStatus.PENDING);
        email.setCreatedAt(now);
        email.setNextAttemptAt(now);
        outbox.save(email);
    }

    public void verify(String token) {
        String hash = VerificationTokenUtil.hash(token);
        UUID id = verifications.findUserIdByTokenHash(hash).orElseThrow(this::invalidToken);
        // Same lock order for resend and verification; never load the token entity before locking user.
        UserModel user = users.findLockedById(id).orElseThrow(this::invalidToken);
        EmailVerificationModel verification = verifications.findById(id).orElseThrow(this::invalidToken);
        if (!hash.equals(verification.getTokenHash()) || verification.getConsumedAt() != null
                || !verification.getExpiresAt().isAfter(Instant.now())) throw invalidToken();
        user.setEmailVerifiedAt(Instant.now());
        user.setEnabled(true);
        verification.setConsumedAt(Instant.now());
    }

    private BusinessException invalidToken() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "Código inválido, vencido o ya utilizado");
    }
}
