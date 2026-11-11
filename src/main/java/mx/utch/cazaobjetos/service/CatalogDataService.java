package mx.utch.cazaobjetos.service;

import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.enums.*;
import mx.utch.cazaobjetos.exception.BusinessException;
import mx.utch.cazaobjetos.repository.*;
import mx.utch.cazaobjetos.repository.specification.CatalogSpecification;

@Service
public class CatalogDataService {
    private final FoundItemRepository found;
    private final LostReportRepository lost;
    public CatalogDataService(FoundItemRepository found, LostReportRepository lost) {
        this.found = found;
        this.lost = lost;
    }
    public PageResponseDto<FoundItemResponseDto> found(CatalogQueryDto query) { return found(query, null); }
    public PageResponseDto<FoundItemResponseDto> found(CatalogQueryDto query, java.util.UUID actor) {
        var spec = CatalogSpecification.<mx.utch.cazaobjetos.model.FoundItemModel>matching(
                query, "foundLocation", "foundOn", parseStatus(FoundStatus.class, query.status()));
        spec = spec.and((root, criteria, cb) -> actor == null
                ? cb.notEqual(root.get("status"), FoundStatus.WITHDRAWN)
                : cb.or(cb.equal(root.get("reporterId"), actor), cb.equal(root.get("responsibleId"), actor)));
        return PageResponseDto.from(found.findAll(spec, pageable(query, "foundOn")).map(item -> item.toResponseDto()));
    }
    public PageResponseDto<LostReportResponseDto> lost(CatalogQueryDto query) { return lost(query, null); }
    public PageResponseDto<LostReportResponseDto> lost(CatalogQueryDto query, java.util.UUID actor) {
        if (query.custody() != null) throw new BusinessException(HttpStatus.BAD_REQUEST, "custody solo aplica a hallazgos");
        var spec = CatalogSpecification.<mx.utch.cazaobjetos.model.LostReportModel>matching(
                query, "lostLocation", "lostOn", parseStatus(LostStatus.class, query.status()));
        spec = spec.and((root, criteria, cb) -> actor == null
                ? cb.notEqual(root.get("status"), LostStatus.WITHDRAWN)
                : cb.equal(root.get("ownerId"), actor));
        return PageResponseDto.from(lost.findAll(spec, pageable(query, "lostOn")).map(item -> item.toResponseDto()));
    }
    private <T extends Enum<T>> T parseStatus(Class<T> type, String status) {
        if (status == null) return null;
        try { return Enum.valueOf(type, status); }
        catch (IllegalArgumentException invalid) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Estado inválido para este catálogo");
        }
    }
    private Pageable pageable(CatalogQueryDto query, String dateField) {
        Sort.Order primary = switch (query.sort()) {
            case RECENT -> Sort.Order.desc(dateField);
            case OLDEST -> Sort.Order.asc(dateField);
            case TITLE_ASC -> Sort.Order.asc("title").ignoreCase();
            case TITLE_DESC -> Sort.Order.desc("title").ignoreCase();
        };
        // The UUID tie-breaker makes equal titles/dates deterministic across pages.
        return PageRequest.of(query.page(), query.pageSize(), Sort.by(primary, Sort.Order.asc("id")));
    }
}
