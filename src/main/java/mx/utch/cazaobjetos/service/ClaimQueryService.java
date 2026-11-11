package mx.utch.cazaobjetos.service;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.annotation.*;
import mx.utch.cazaobjetos.dto.*;
@Service
@Validated
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ClaimQueryService {
    private final ClaimQueryDataService data;
    private final ItemDataService items;
    private final UserDataService users;
    public ClaimQueryService(ClaimQueryDataService data,ItemDataService items,UserDataService users) { this.data=data;this.items=items;this.users=users; }
    public PageResponseDto<ClaimResponseDto> search(UUID actor,@Valid ClaimQueryDto query) { return data.search(actor,query); }
    public ClaimDetailDto detail(UUID actor,UUID id) {
        var claim=data.detail(actor,id);
        var found=items.found(claim.foundItemId());
        return new ClaimDetailDto(claim,found.toResponseDto(),DirectoryDataService.person(users.user(found.getResponsibleId())),
            DirectoryDataService.person(users.user(claim.claimantId())));
    }
}
