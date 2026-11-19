package mx.utch.cazaobjetos.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class RegistrationMode {
    private final boolean demo;
    public RegistrationMode(Environment environment, @Value("${app.auth.demo-verification:false}") boolean configured) {
        demo=configured && environment.acceptsProfiles(Profiles.of("dev"));
    }
    public boolean demoVerification() { return demo; }
}
