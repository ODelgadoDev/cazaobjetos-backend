package mx.utch.cazaobjetos.config;

import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import mx.utch.cazaobjetos.repository.UserRepository;
import mx.utch.cazaobjetos.model.UserModel;
import mx.utch.cazaobjetos.enums.UserRole;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("dev")
class DevelopmentAccounts {
    @Bean CommandLineRunner seed(UserRepository users, PasswordEncoder encoder, Environment env) {
        return args -> {
            String password = env.getRequiredProperty("DEV_PASSWORD");
            if (password.length() < 12) throw new IllegalArgumentException("DEV_PASSWORD requiere 12 caracteres como mínimo");
            String[] names = {"alumno.demo", "reclamante.demo", "tercero.demo", "docente.demo", "secretaria.demo"};
            for (String name : names) {
                if (users.existsByEmail(name + "@utch.edu.mx")) continue;
                UserModel user = new UserModel();
                user.setId(UUID.randomUUID());
                user.setEmail(name + "@utch.edu.mx");
                user.setPasswordHash(encoder.encode(password));
                user.setDisplayName(name);
                user.setRole(name.startsWith("docente") ? UserRole.TEACHER : name.startsWith("secretaria") ? UserRole.SECRETARY : UserRole.STUDENT);
                user.setEnabled(true);
                user.setCreatedAt(Instant.now());
                users.save(user);
            }
        };
    }
}
