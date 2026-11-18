package mx.utch.cazaobjetos.service;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;
@Component
public class PushEndpointValidator {
    private final List<String> hosts;
    public PushEndpointValidator(@Value("${app.push.allowed-hosts:fcm.googleapis.com,updates.push.services.mozilla.com,web.push.apple.com,*.notify.windows.com}") String hosts) {this.hosts=Arrays.stream(hosts.split(",")).map(String::strip).map(String::toLowerCase).toList();}
    public void validate(String endpoint) {
        try {
            URI url=URI.create(endpoint);String host=url.getHost()==null?"":url.getHost().toLowerCase(Locale.ROOT);
            require(endpoint.length()<=2048&&"https".equals(url.getScheme())&&(url.getPort()==-1||url.getPort()==443)&&url.getRawUserInfo()==null&&url.getRawFragment()==null&&!url.getRawPath().isEmpty(),HttpStatus.BAD_REQUEST,"Endpoint push inválido");
            require(hosts.stream().anyMatch(h->h.startsWith("*.")?host.endsWith(h.substring(1))&&host.length()>h.length()-1:host.equals(h)),HttpStatus.BAD_REQUEST,"Servicio push no permitido");
        } catch(IllegalArgumentException error){throw new mx.utch.cazaobjetos.exception.BusinessException(HttpStatus.BAD_REQUEST,"Endpoint push inválido");}
    }
}
