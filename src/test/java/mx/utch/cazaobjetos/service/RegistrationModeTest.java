package mx.utch.cazaobjetos.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationModeTest {
    @Test void flagRequiresDevelopmentProfile() {
        var environment=new MockEnvironment();
        assertFalse(new RegistrationMode(environment,true,false).demoVerification());
        environment.setActiveProfiles("prod");
        assertFalse(new RegistrationMode(environment,true,false).demoVerification());
        environment.setActiveProfiles("dev");
        assertFalse(new RegistrationMode(environment,false,false).demoVerification());
        assertTrue(new RegistrationMode(environment,true,false).demoVerification());
    }
}
