package mx.utch.cazaobjetos.config;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import mx.utch.cazaobjetos.model.UserModel;
public class VersionedUser extends User {
    private static final long serialVersionUID=1L;
    private final long credentialsVersion;
    public VersionedUser(UserModel user) {
        super(user.getEmail(),user.getPasswordHash(),List.of(new SimpleGrantedAuthority("ROLE_"+user.getRole().name())));
        credentialsVersion=user.getCredentialsVersion();
    }
    public long credentialsVersion() { return credentialsVersion; }
}
