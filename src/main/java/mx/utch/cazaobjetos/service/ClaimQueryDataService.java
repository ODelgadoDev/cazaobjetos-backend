package mx.utch.cazaobjetos.service;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.repository.ClaimRepository;
import mx.utch.cazaobjetos.exception.BusinessException;
@Service
public class ClaimQueryDataService {
    private final ClaimRepository claims;
    public ClaimQueryDataService(ClaimRepository claims) { this.claims=claims; }
    public PageResponseDto<ClaimResponseDto> search(UUID actor,ClaimQueryDto query) {
        return PageResponseDto.from(claims.search(actor,query.view().name(),query.status(),query.foundItemId(),null,PageRequest.of(query.page(),query.pageSize())));
    }
    public ClaimResponseDto detail(UUID actor,UUID id) {
        return claims.search(actor,"ALL",null,null,id,PageRequest.of(0,1)).stream().findFirst()
            .orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"Reclamo no disponible"));
    }
}
