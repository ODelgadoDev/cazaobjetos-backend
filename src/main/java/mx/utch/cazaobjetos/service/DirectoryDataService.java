package mx.utch.cazaobjetos.service;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.UserRole;
import mx.utch.cazaobjetos.model.UserModel;
import mx.utch.cazaobjetos.repository.UserRepository;
@Service
public class DirectoryDataService {
    private final UserRepository users;
    public DirectoryDataService(UserRepository users) { this.users=users; }
    public PageResponseDto<DirectoryUserDto> list(DirectoryQueryDto query) {
        String pattern="%"+query.q().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        Specification<UserModel> spec=(root,criteria,cb)->cb.and(
            cb.isTrue(root.get("enabled")),root.get("role").in(UserRole.TEACHER,UserRole.SECRETARY),
            query.role()==null?cb.conjunction():cb.equal(root.get("role"),query.role()),
            cb.or(cb.like(cb.lower(root.get("displayName")),pattern,'!'),cb.like(cb.lower(root.get("email")),pattern,'!')));
        return PageResponseDto.from(users.findAll(spec,PageRequest.of(query.page(),query.pageSize(),
            Sort.by(Sort.Order.asc("displayName").ignoreCase(),Sort.Order.asc("id")))).map(DirectoryDataService::person));
    }
    public static DirectoryUserDto person(UserModel user) {
        return new DirectoryUserDto(user.getId(),user.getDisplayName(),user.getEmail(),user.getRole());
    }
}
