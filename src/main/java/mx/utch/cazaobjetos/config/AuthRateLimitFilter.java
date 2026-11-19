package mx.utch.cazaobjetos.config;

import java.io.IOException;
import java.time.Clock;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded local fixed-window limiter for one backend instance; does not trust forwarded headers. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private record Window(long start, int count) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Set<String> paths;
    private final Clock clock;

    public AuthRateLimitFilter(String api) { this(api, Clock.systemUTC()); }
    public AuthRateLimitFilter(String api, Clock clock) {
        this.clock = clock;
        paths = Set.of(api + "/auth/login", api + "/auth/register",
                api + "/auth/verify-email", api + "/auth/verify-email/demo", api + "/auth/resend-verification",
                api + "/auth/forgot-password", api + "/auth/reset-password", api + "/auth/change-password");
    }
    private synchronized boolean allow(String ip) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().start() >= 60_000);
        Window window = windows.get(ip);
        if (window == null) {
            if (windows.size() >= 10_000) return false;
            windows.put(ip, new Window(now, 1));
            return true;
        }
        if (window.count() >= 30) return false;
        windows.put(ip, new Window(window.start(), window.count() + 1));
        return true;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && paths.contains(request.getServletPath())
                && !allow(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"message\":\"Demasiados intentos. Espera un minuto.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
