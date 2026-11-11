package mx.utch.cazaobjetos.dto;
import jakarta.validation.constraints.*;
import mx.utch.cazaobjetos.enums.UserRole;
public record DirectoryQueryDto(@Size(max=200) String q,UserRole role,
        @Min(0) @Max(10000) Integer page,@Min(1) @Max(100) Integer pageSize) {
    public DirectoryQueryDto { q=q==null?"":q.strip();page=page==null?0:page;pageSize=pageSize==null?20:pageSize; }
}
