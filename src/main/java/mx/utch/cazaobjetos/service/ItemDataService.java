package mx.utch.cazaobjetos.service;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import mx.utch.cazaobjetos.constants.Constants;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.exception.BusinessException;
import mx.utch.cazaobjetos.model.*;
import mx.utch.cazaobjetos.repository.*;
import static mx.utch.cazaobjetos.util.ValidateUtil.require;

/** Persistence operations; the orchestrating service owns the transaction boundary. */
@Service
public class ItemDataService {
    private final FoundItemRepository found;
    private final LostReportRepository lost;
    private final ClaimRepository claims;

    public ItemDataService(FoundItemRepository found, LostReportRepository lost, ClaimRepository claims) {
        this.found = found;
        this.lost = lost;
        this.claims = claims;
    }

    private BusinessException missing() { return new BusinessException(HttpStatus.NOT_FOUND, Constants.NOT_FOUND); }
    private PageRequest page(int page) {
        require(page >= 0 && page <= Constants.MAX_PAGE, HttpStatus.BAD_REQUEST, "Página fuera de rango");
        return PageRequest.of(page, Constants.PAGE_SIZE, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
    }

    public LostReportModel lost(UUID id) { return lost.findById(id).orElseThrow(this::missing); }
    public boolean hasActiveClaims(UUID id) {
        return claims.existsByFoundItemIdAndStatusIn(id, List.of(ClaimStatus.PENDING, ClaimStatus.ACCEPTED));
    }
    public FoundItemModel found(UUID id) { return found.findById(id).orElseThrow(this::missing); }
    public FoundItemModel lockItem(UUID id) { return found.findLockedById(id).orElseThrow(this::missing); }
    public LostReportModel lockLoss(UUID id) { return lost.findLockedById(id).orElseThrow(this::missing); }
    public ClaimModel claim(UUID id) { return claims.findById(id).orElseThrow(this::missing); }
    // Scalar query avoids loading stale claim state before waiting on the parent lock.
    public UUID itemIdForClaim(UUID id) { return claims.findItemIdById(id).orElseThrow(this::missing); }
    public List<ClaimResponseDto> claims(UUID actor, int page) { return claims.findVisibleTo(actor, page(page)); }
    public boolean hasAcceptedClaim(UUID item) { return claims.existsByFoundItemIdAndStatus(item, ClaimStatus.ACCEPTED); }
    public boolean hasActiveClaim(UUID item, UUID user) {
        return claims.existsByFoundItemIdAndClaimantIdAndStatusIn(item, user, List.of(ClaimStatus.PENDING, ClaimStatus.ACCEPTED));
    }
    public boolean hasActiveLinkedClaim(UUID loss) {
        return claims.existsByLostReportIdAndStatusIn(loss, List.of(ClaimStatus.PENDING, ClaimStatus.ACCEPTED));
    }
    public List<ClaimModel> closeOtherClaims(UUID item) {
        var pending=claims.findByFoundItemIdAndStatus(item,ClaimStatus.PENDING);
        pending.forEach(c->c.setStatus(ClaimStatus.CLOSED));return pending;
    }

    public FoundItemModel createFound(UUID actor, ReportRequestDto request) {
        FoundItemModel item = new FoundItemModel();
        item.setId(UUID.randomUUID());
        item.setReporterId(actor);
        item.setResponsibleId(actor);
        item.setTitle(request.title().strip());
        item.setDescription(request.description().strip());
        item.setCategory(request.category());
        item.setFoundLocation(request.location().strip());
        item.setFoundOn(request.occurredOn());
        item.setStatus(FoundStatus.AVAILABLE);
        item.setCustody(CustodyStatus.WITH_FINDER);
        item.setCreatedAt(Instant.now());
        return found.save(item);
    }
    public LostReportModel createLost(UUID actor, ReportRequestDto request) {
        LostReportModel loss = new LostReportModel();
        loss.setId(UUID.randomUUID());
        loss.setOwnerId(actor);
        loss.setTitle(request.title().strip());
        loss.setDescription(request.description().strip());
        loss.setCategory(request.category());
        loss.setLostLocation(request.location().strip());
        loss.setLostOn(request.occurredOn());
        loss.setStatus(LostStatus.ACTIVE);
        loss.setCreatedAt(Instant.now());
        return lost.save(loss);
    }
    public ClaimModel createClaim(UUID actor, UUID item, ClaimRequestDto request) {
        ClaimModel claim = new ClaimModel();
        claim.setId(UUID.randomUUID());
        claim.setFoundItemId(item);
        claim.setClaimantId(actor);
        claim.setLostReportId(request.lostReportId());
        claim.setPrivateDescription(request.privateDescription().strip());
        claim.setStatus(ClaimStatus.PENDING);
        claim.setCreatedAt(Instant.now());
        return claims.saveAndFlush(claim);
    }
}
