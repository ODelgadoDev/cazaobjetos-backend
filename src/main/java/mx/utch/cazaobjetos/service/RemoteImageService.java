package mx.utch.cazaobjetos.service;

import java.io.*;
import java.net.*;
import java.time.Duration;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.*;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import mx.utch.cazaobjetos.exception.BusinessException;
import mx.utch.cazaobjetos.util.PublicImageDnsResolver;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;

@Service
public class RemoteImageService {
    private static final int MAX_BYTES = 5 * 1024 * 1024;

    public static URI validateUrl(String raw) {
        try {
            URI uri = URI.create(raw.strip());
            require("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getFragment() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443),
                    HttpStatus.BAD_REQUEST, "Usa el enlace HTTPS directo de una imagen pública");
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "El enlace de la imagen no es válido");
        }
    }

    public byte[] download(String url) {
        URI uri = validateUrl(url);
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        var connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new PublicImageDnsResolver())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(5)).setSocketTimeout(Timeout.ofSeconds(5)).build())
                .build();
        try (var client = HttpClients.custom().setConnectionManager(connections)
                .disableAutomaticRetries().disableRedirectHandling().disableCookieManagement()
                .disableContentCompression().disableAuthCaching()
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(5))
                        .setConnectionRequestTimeout(Timeout.ofSeconds(5)).build()).build()) {
            for (int redirects = 0; redirects <= 3; redirects++) {
                require(System.nanoTime() < deadline, HttpStatus.BAD_GATEWAY, "El servidor de la imagen tardó demasiado");
                // Also validates IP literals when the HTTP connector does not perform DNS resolution.
                new PublicImageDnsResolver().resolve(uri.getHost());
                var get = new HttpGet(uri);
                get.setHeader("Accept", "image/jpeg,image/png");
                get.setHeader("User-Agent", "CazaObjetos-ImageImporter/1.0");
                try (var response = client.executeOpen(null, get, null)) {
                    try {
                        int status = response.getCode();
                        if (status >= 300 && status < 400) {
                            var location = response.getFirstHeader("Location");
                            require(location != null && redirects < 3, HttpStatus.BAD_REQUEST, "El enlace tiene demasiadas redirecciones");
                            uri = validateUrl(uri.resolve(location.getValue()).toString());
                            continue;
                        }
                        require(status == 200 && response.getEntity() != null, HttpStatus.BAD_REQUEST,
                                "No se pudo descargar la imagen. Prueba copiarla o subirla desde tu dispositivo.");
                        require(response.getEntity().getContentLength() <= MAX_BYTES, HttpStatus.PAYLOAD_TOO_LARGE, "La imagen excede 5 MB");
                        var input = response.getEntity().getContent();
                        try (var output = new ByteArrayOutputStream()) {
                            byte[] buffer = new byte[8192];
                            int count;
                            while ((count = input.read(buffer)) != -1) {
                                require(System.nanoTime() < deadline, HttpStatus.BAD_GATEWAY, "El servidor de la imagen tardó demasiado");
                                require(output.size() + count <= MAX_BYTES, HttpStatus.PAYLOAD_TOO_LARGE, "La imagen excede 5 MB");
                                output.write(buffer, 0, count);
                            }
                            return output.toByteArray();
                        }
                    } finally {
                        // Abort instead of draining an oversized or slow response on close.
                        get.cancel();
                    }
                }
            }
            throw new BusinessException(HttpStatus.BAD_REQUEST, "No se pudo importar el enlace");
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "El enlace no está disponible o no apunta a una imagen pública. Prueba copiarla o subirla desde tu dispositivo.");
        }
    }
}
