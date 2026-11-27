package mx.utch.cazaobjetos.config;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mx.utch.cazaobjetos.service.AccountService;
import org.springframework.web.filter.OncePerRequestFilter;

final class PendingRegistrationActivationFilter extends OncePerRequestFilter {
    private final String loginPath;
    private final AccountService accounts;

    PendingRegistrationActivationFilter(String api, AccountService accounts) {
        loginPath = api + "/auth/login";
        this.accounts = accounts;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !accounts.openRegistration()
                || !"POST".equals(request.getMethod())
                || !loginPath.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        accounts.activatePendingForLogin(request.getParameter("username"), request.getParameter("password"));
        chain.doFilter(request, response);
    }
}
