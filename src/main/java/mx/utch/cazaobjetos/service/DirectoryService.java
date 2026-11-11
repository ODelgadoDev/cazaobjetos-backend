package mx.utch.cazaobjetos.service;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.UserRole;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;
@Service
@Validated
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class DirectoryService {
    private final DirectoryDataService data;
    public DirectoryService(DirectoryDataService data) { this.data=data; }
    public PageResponseDto<DirectoryUserDto> list(@Valid DirectoryQueryDto query) {
        require(query.role()!=UserRole.STUDENT,HttpStatus.BAD_REQUEST,"El directorio solo incluye docentes y secretaria");
        return data.list(query);
    }
}
