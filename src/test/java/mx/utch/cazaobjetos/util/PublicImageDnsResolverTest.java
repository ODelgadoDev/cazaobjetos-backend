package mx.utch.cazaobjetos.util;
import java.net.*;
import org.junit.jupiter.api.Test;
import mx.utch.cazaobjetos.service.RemoteImageService;
import mx.utch.cazaobjetos.exception.BusinessException;
import static org.junit.jupiter.api.Assertions.*;
class PublicImageDnsResolverTest {
    @Test void rejectsPrivateAndSpecialNetworkAddressesIncludingIpv6() throws Exception {
        for (String ip : new String[]{"127.0.0.1","10.0.0.1","172.16.2.1","192.168.1.1","169.254.169.254","100.64.0.1","0.0.0.0","198.18.0.1","192.0.2.1","224.0.0.1","::1","fc00::1","fe80::1","2001:db8::1","2002:7f00:1::"}) {
            assertFalse(PublicImageDnsResolver.isPublic(InetAddress.getByName(ip)),ip);
        }
        assertTrue(PublicImageDnsResolver.isPublic(InetAddress.getByName("8.8.8.8")));
        assertTrue(PublicImageDnsResolver.isPublic(InetAddress.getByName("2606:4700:4700::1111")));
        assertThrows(UnknownHostException.class,()->new PublicImageDnsResolver().resolve("127.0.0.1"));
    }
    @Test void rejectsUnsupportedUrlsAndCredentials() {
        for(String url:new String[]{"file:///etc/passwd","http://example.org/image.png","https://user:pass@example.org/image.png","https://example.org:8080/image.png","https://example.org/image#fragment","bad url"})
            assertThrows(BusinessException.class,()->RemoteImageService.validateUrl(url),url);
        assertEquals("https://example.org/photo.png?size=500",RemoteImageService.validateUrl("https://example.org/photo.png?size=500").toString());
    }
}
