package mx.utch.cazaobjetos.service;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import mx.utch.cazaobjetos.repository.*;
import mx.utch.cazaobjetos.model.*;
import mx.utch.cazaobjetos.enums.EmailStatus;
import mx.utch.cazaobjetos.exception.BusinessException;
import mx.utch.cazaobjetos.util.PasswordPolicy;
import mx.utch.cazaobjetos.util.VerificationTokenUtil;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;
@Service
@Transactional
public class PasswordService {
    private final UserRepository users;
    private final PasswordResetRepository resets;
    private final EmailOutboxRepository outbox;
    private final PasswordEncoder passwords;
    private final ItemEventService events;
    private final RegistrationMode registration;
    public PasswordService(UserRepository users,PasswordResetRepository resets,EmailOutboxRepository outbox,PasswordEncoder passwords,ItemEventService events,RegistrationMode registration) {
        this.users=users;this.resets=resets;this.outbox=outbox;this.passwords=passwords;this.events=events;this.registration=registration;
    }
    public void forgot(String email) {
        if (registration.openRegistration()) return;
        UUID id=users.findIdByEmail(email.strip().toLowerCase(Locale.ROOT)).orElse(null);
        if(id==null) return;
        var user=users.findLockedById(id).orElseThrow();
        if(!user.getEnabled()) return; // Never enables an unverified/disabled account.
        var reset=resets.findById(id).orElseGet(PasswordResetModel::new);
        Instant now=Instant.now();
        if(reset.getSentAt()!=null && reset.getSentAt().plusSeconds(60).isAfter(now)) return;
        String token=VerificationTokenUtil.generate();
        reset.setUserId(id);reset.setTokenHash(VerificationTokenUtil.hash(token));reset.setSentAt(now);
        reset.setExpiresAt(now.plusSeconds(1800));reset.setConsumedAt(null);resets.save(reset);
        var mail=new EmailOutboxModel();mail.setId(UUID.randomUUID());mail.setEventKey("RESET:"+UUID.randomUUID());
        mail.setRecipient(user.getEmail());mail.setSubject("Recupera tu acceso a CazaObjetos UTCH");
        mail.setBody("Si no solicitaste cambiar tu contraseña, ignora este mensaje.\n\nCódigo de recuperación (válido durante 30 minutos):\n"+token+
            "\n\nEnvía el código y tu nueva contraseña a POST /api/v1/auth/reset-password. Solo el código más reciente es válido.");
        mail.setStatus(EmailStatus.PENDING);mail.setCreatedAt(now);mail.setNextAttemptAt(now);outbox.save(mail);
    }
    private BusinessException invalid() { return new BusinessException(HttpStatus.BAD_REQUEST,"Código inválido, vencido o ya utilizado"); }
    private void update(UserModel user,String password) {
        PasswordPolicy.validate(password);
        user.setPasswordHash(passwords.encode(password));
        user.setCredentialsVersion(user.getCredentialsVersion()+1);
        resets.findById(user.getId()).ifPresent(reset->reset.setConsumedAt(Instant.now()));
        events.audit(user.getId(),user.getId(),"PASSWORD_CHANGED");
    }
    public void reset(String token,String password) {
        if (registration.openRegistration()) throw invalid();
        String hash=VerificationTokenUtil.hash(token);
        UUID id=resets.findUserIdByTokenHash(hash).orElseThrow(this::invalid);
        var user=users.findLockedById(id).orElseThrow(this::invalid);
        var reset=resets.findById(id).orElseThrow(this::invalid);
        if(!user.getEnabled() || !hash.equals(reset.getTokenHash()) || reset.getConsumedAt()!=null || !reset.getExpiresAt().isAfter(Instant.now())) throw invalid();
        update(user,password);
    }
    public void change(UUID actor,String current,String password) {
        var user=users.findLockedById(actor).orElseThrow(this::invalid);
        require(user.getEnabled(),HttpStatus.FORBIDDEN,"Cuenta no disponible");
        require(current.getBytes(StandardCharsets.UTF_8).length<=72 && passwords.matches(current,user.getPasswordHash()),HttpStatus.BAD_REQUEST,"La contraseña actual no es correcta");
        update(user,password);
    }
}
