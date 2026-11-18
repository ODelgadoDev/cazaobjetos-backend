package mx.utch.cazaobjetos.service;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.security.SecureRandom;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import mx.utch.cazaobjetos.dto.*;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;
@Service
@Transactional
public class PushSubscriptionService {
    private final PushDataService data;
    private final PushEndpointValidator validator;
    private final VapidKeys keys;
    private final Clock clock;
    public PushSubscriptionService(PushDataService data,PushEndpointValidator validator,VapidKeys keys,Clock clock){this.data=data;this.validator=validator;this.keys=keys;this.clock=clock;}
    public static String hash(String value){return mx.utch.cazaobjetos.util.EvidenceImageUtil.hash(value.getBytes(StandardCharsets.UTF_8));}
    private String normalize(String text,int bytes){try {var decoded=Base64.getUrlDecoder().decode(text);require(decoded.length==bytes,HttpStatus.BAD_REQUEST,"Claves de suscripción inválidas");return Base64.getUrlEncoder().withoutPadding().encodeToString(decoded);}catch(IllegalArgumentException error){throw new mx.utch.cazaobjetos.exception.BusinessException(HttpStatus.BAD_REQUEST,"Claves de suscripción inválidas");}}
    public PushSubscriptionResponseDto register(UUID user,String sessionId,PushSubscriptionRequestDto request){
        require(user.equals(request.expectedUserId()),HttpStatus.CONFLICT,"La cuenta cambió; vuelve a iniciar sesión antes de activar avisos");
        require(keys.enabled(),HttpStatus.CONFLICT,"Notificaciones del dispositivo desactivadas en el servidor");validator.validate(request.endpoint());
        String publicKey=normalize(request.keys().p256dh(),65),auth=normalize(request.keys().auth(),16);
        try {nl.martijndwars.webpush.Utils.loadPublicKey(publicKey);}catch(Exception error){throw new mx.utch.cazaobjetos.exception.BusinessException(HttpStatus.BAD_REQUEST,"Clave pública de suscripción inválida");}
        String endpointHash=hash(request.endpoint());data.lockEndpoint(endpointHash);long credentials=data.lockUser(user);data.expireCredentials(user,credentials);var old=data.byHash(endpointHash).orElse(null);
        require(old==null||old.owner().equals(user)||(old.p256dh().equals(publicKey)&&old.auth().equals(auth)),HttpStatus.CONFLICT,"La suscripción pertenece a otra vinculación del dispositivo");
        require(old!=null&&old.owner().equals(user)&&old.active()||data.activeCount(user)<10,HttpStatus.CONFLICT,"Máximo 10 dispositivos por cuenta");
        boolean reuse=old!=null&&old.active()&&old.owner().equals(user)&&old.credentials()==credentials&&old.p256dh().equals(publicKey)&&old.auth().equals(auth);
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);String binding=reuse?old.binding():Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        var sub=new PushDataService.Subscription(old==null?UUID.randomUUID():old.id(),user,request.endpoint(),publicKey,auth,binding,hash(sessionId),credentials,true);
        if(old==null)data.insert(sub,endpointHash,clock.instant());else data.update(sub,clock.instant());return new PushSubscriptionResponseDto(sub.id(),user,binding);
    }
    public void disable(UUID user,UUID id){var sub=data.lock(id).orElse(null);require(sub!=null&&sub.owner().equals(user),HttpStatus.NOT_FOUND,"Dispositivo no disponible");data.disable(id);}
    public void loginSession(String sessionId){if(sessionId!=null)data.loginSession(hash(sessionId));}
    public void logout(UUID user,String sessionId){if(sessionId!=null)data.logout(user,hash(sessionId));}
    public UUID test(UUID user,String sessionId,UUID id){var sub=data.lock(id).orElse(null);require(sub!=null&&sub.owner().equals(user)&&sub.active()&&sub.session().equals(hash(sessionId))&&data.credentialsValid(sub),HttpStatus.NOT_FOUND,"Dispositivo no disponible");require(!data.recentTest(id,clock.instant().minusSeconds(60)),HttpStatus.TOO_MANY_REQUESTS,"Espera un minuto antes de repetir la prueba");return data.queue(sub,"TEST","test:"+UUID.randomUUID(),clock.instant());}
}
