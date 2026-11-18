package mx.utch.cazaobjetos.config;
import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
/** Remember the old device session before Spring rotates its identifier on successful login. */
public class PushLoginSessionFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE="caza.previous.push.session";
    private final String loginPath;
    public PushLoginSessionFilter(String api){loginPath=api+"/auth/login";}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        if("POST".equals(request.getMethod())&&loginPath.equals(request.getServletPath())){
            var session=request.getSession(false);request.setAttribute(ATTRIBUTE,session==null?request.getRequestedSessionId():session.getId());
        }
        chain.doFilter(request,response);
    }
}
