package mx.utch.cazaobjetos.service;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import nl.martijndwars.webpush.Notification;
@Component
public class WebPushTransport implements PushTransport {
    private final VapidKeys keys;private final PushEndpointValidator endpoints;private final String subject;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    public WebPushTransport(VapidKeys keys,PushEndpointValidator endpoints,@Value("${app.push.subject:https://utch.edu.mx}") String subject){this.keys=keys;this.endpoints=endpoints;this.subject=subject;}
    public Result send(PushDataService.Subscription s,byte[] payload)throws Exception {
        endpoints.validate(s.endpoint());var encrypted=new PushCodec(keys.pair(),subject).encode(new Notification(s.endpoint(),s.p256dh(),s.auth(),payload,3600));
        var request=java.net.http.HttpRequest.newBuilder(URI.create(encrypted.getUrl())).timeout(Duration.ofSeconds(6));encrypted.getHeaders().forEach(request::header);
        var response=client.send(request.POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(encrypted.getBody())).build(),HttpResponse.BodyHandlers.discarding());long retry=0;
        try{retry=Long.parseLong(response.headers().firstValue("Retry-After").orElse("0"));}catch(NumberFormatException ignored){try{retry=java.time.Duration.between(java.time.Instant.now(),java.time.ZonedDateTime.parse(response.headers().firstValue("Retry-After").orElse(""),java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds();}catch(java.time.format.DateTimeParseException invalid){}}
        return new Result(response.statusCode(),Math.max(0,Math.min(retry,3600)));
    }
}
