package mx.utch.cazaobjetos.dto;
import java.util.UUID;
import mx.utch.cazaobjetos.enums.UserRole;
public record DirectoryUserDto(UUID id,String displayName,String email,UserRole role) {}
