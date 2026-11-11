package mx.utch.cazaobjetos.config;
import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import mx.utch.cazaobjetos.repository.UserRepository;
public class SessionVersionFilter extends OncePerRequestFilter {
    private final UserRepository users;
    public SessionVersionFilter(UserRepository users) { this.users=users; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.getPrincipal() instanceof VersionedUser principal) {
            var user=users.findByEmailAndEnabledTrue(principal.getUsername()).orElse(null);
            if(user==null || user.getCredentialsVersion()!=principal.credentialsVersion()) {
                SecurityContextHolder.clearContext();var session=request.getSession(false);if(session!=null) session.invalidate();
                response.setStatus(401);return;
            }
        }
        chain.doFilter(request,response);
    }
}
