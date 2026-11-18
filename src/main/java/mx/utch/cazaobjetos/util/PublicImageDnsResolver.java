package mx.utch.cazaobjetos.util;

import java.net.*;
import org.apache.hc.client5.http.DnsResolver;

/** Returns only public addresses to the connector, so DNS checks and connections share the same result. */
public class PublicImageDnsResolver implements DnsResolver {
    @Override public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            if (!isPublic(address)) throw new UnknownHostException("El enlace debe apuntar a un servidor público");
        }
        return addresses;
    }
    @Override public String resolveCanonicalHostname(String host) throws UnknownHostException {
        resolve(host);
        return host;
    }
    public static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 255;
        int second = bytes[1] & 255;
        if (bytes.length == 16) {
            // Global unicast, excluding special-purpose and 6to4 ranges.
            return (first & 0xe0) == 0x20
                    && !(first == 0x20 && second == 0x01 && (bytes[2] & 255) < 2)
                    && !(first == 0x20 && second == 0x02)
                    && !(first == 0x20 && second == 0x01 && (bytes[2] & 255) == 0x0d && (bytes[3] & 255) == 0xb8)
                    && !(first == 0x3f && second == 0xff);
        }
        int third = bytes[2] & 255;
        return first != 0 && first != 10 && first != 127 && first < 224
                && !(first == 100 && second >= 64 && second <= 127)
                && !(first == 169 && second == 254)
                && !(first == 172 && second >= 16 && second <= 31)
                && !(first == 192 && (second == 168 || second == 0 || second == 88 && third == 99))
                && !(first == 198 && (second == 18 || second == 19 || second == 51 && third == 100))
                && !(first == 203 && second == 0 && third == 113);
    }
}
