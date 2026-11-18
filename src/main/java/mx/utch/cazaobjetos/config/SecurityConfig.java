package mx.utch.cazaobjetos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import mx.utch.cazaobjetos.repository.UserRepository;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {
    @Bean PasswordEncoder passwords() { return new BCryptPasswordEncoder(); }

    @Bean UserDetailsService users(UserRepository users) {
        return email -> users.findByEmailAndEnabledTrue(email.strip().toLowerCase(java.util.Locale.ROOT))
                .map(VersionedUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("Cuenta no disponible"));
    }

    @Bean SecurityFilterChain security(HttpSecurity http, @org.springframework.beans.factory.annotation.Value("${url.api}") String api, UserRepository users, mx.utch.cazaobjetos.service.PushSubscriptionService push, mx.utch.cazaobjetos.service.UserDataService userData) throws Exception {
        // Cookie session with CSRF protection. No caller-supplied user IDs or roles.
        return http.cors(org.springframework.security.config.Customizer.withDefaults()).addFilterBefore(new SessionVersionFilter(users), org.springframework.security.web.csrf.CsrfFilter.class)
                .addFilterBefore(new AuthRateLimitFilter(api), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new PushLoginSessionFilter(api), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                    .requestMatchers(api + "/openapi.json", api + "/health", api + "/auth/csrf", api + "/auth/login",
                        api + "/auth/register", api + "/auth/forgot-password", api + "/auth/reset-password", api + "/auth/verify-email", api + "/auth/resend-verification").permitAll()
                    .anyRequest().authenticated())
                .formLogin(f -> f.loginProcessingUrl(api + "/auth/login")
                    .successHandler((req,res,auth) -> {push.loginSession((String)req.getAttribute(PushLoginSessionFilter.ATTRIBUTE));res.setStatus(204);})
                    .failureHandler((req,res,e) -> res.setStatus(401)))
                .logout(l -> l.logoutUrl(api + "/auth/logout")
                    .addLogoutHandler((req,res,auth) -> {if(auth!=null)push.logout(userData.userId(auth.getName()),req.getRequestedSessionId());})
                    .logoutSuccessHandler((req,res,auth) -> res.setStatus(204)))
                .exceptionHandling(e -> e.accessDeniedHandler((req,res,ex) -> res.setStatus(403))
                    .authenticationEntryPoint((req,res,ex) -> res.setStatus(401)))
                .requestCache(c -> c.disable())
                .build();
    }
}
