package mx.utch.cazaobjetos.config;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthRateLimitFilterTest {
    @Test void limitsPostAttemptsPerAddressAndResetsAfterOneMinute() throws Exception {
        Clock clock = mock(Clock.class); when(clock.millis()).thenReturn(1000L);
        var filter = new AuthRateLimitFilter("/api/v1", clock);
        for (int i=0; i<30; i++) assertEquals(200, request(filter,"127.0.0.1").getStatus());
        var blocked = request(filter,"127.0.0.1");
        assertEquals(429, blocked.getStatus()); assertEquals("60",blocked.getHeader("Retry-After"));
        assertEquals(200, request(filter,"127.0.0.2").getStatus());
        when(clock.millis()).thenReturn(61000L);
        assertEquals(200, request(filter,"127.0.0.1").getStatus());
    }
    private MockHttpServletResponse request(AuthRateLimitFilter filter, String ip) throws Exception {
        var request = new MockHttpServletRequest("POST","/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login"); request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        filter.doFilter(request,response,new MockFilterChain());
        return response;
    }
}
