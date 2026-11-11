package mx.utch.cazaobjetos.service;

import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;
import mx.utch.cazaobjetos.dto.*;

@Service
@Validated
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CatalogService {
    public PageResponseDto<FoundItemResponseDto> myFoundItems(java.util.UUID actor, @Valid CatalogQueryDto query) { return data.found(query, actor); }
    public PageResponseDto<LostReportResponseDto> myLostReports(java.util.UUID actor, @Valid CatalogQueryDto query) { return data.lost(query, actor); }
    private final CatalogDataService data;
    public CatalogService(CatalogDataService data) { this.data = data; }
    public PageResponseDto<FoundItemResponseDto> found(@Valid CatalogQueryDto query) { return data.found(query); }
    public PageResponseDto<LostReportResponseDto> lost(@Valid CatalogQueryDto query) { return data.lost(query); }
}
