package mx.utch.cazaobjetos.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class RegistrationMode {
    private final boolean demo;
    private final boolean openRegistration;

    public RegistrationMode(Environment environment,
                            @Value("${app.auth.demo-verification:false}") boolean demoConfigured,
                            @Value("${app.auth.open-registration:false}") boolean openRegistration) {
        this.openRegistration = openRegistration;
        demo = !openRegistration && demoConfigured && environment.acceptsProfiles(Profiles.of("dev"));
    }

    public boolean demoVerification() { return demo; }
    public boolean openRegistration() { return openRegistration; }
}
