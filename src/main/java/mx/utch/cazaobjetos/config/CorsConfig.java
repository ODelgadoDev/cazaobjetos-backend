package mx.utch.cazaobjetos.config;
import java.net.URI;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.*;
@Configuration
public class CorsConfig {
    @Bean UrlBasedCorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") String raw) {
        List<String> origins=Arrays.stream(raw.split(",")).map(String::strip).filter(s->!s.isEmpty()).toList();
        for(String origin:origins) {
            URI uri=URI.create(origin);
            if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !uri.getPath().isEmpty())
                throw new IllegalArgumentException("CORS requiere orígenes exactos http(s), sin ruta ni comodines");
        }
        var cors=new CorsConfiguration();cors.setAllowedOrigins(origins);cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type","X-CSRF-TOKEN","Idempotency-Key"));cors.setMaxAge(600L);
        var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",cors);return source;
    }
}
