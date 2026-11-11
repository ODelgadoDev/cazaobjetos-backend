package mx.utch.cazaobjetos.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import mx.utch.cazaobjetos.enums.*;

public record CatalogQueryDto(
        @Size(max = 200) String q,
        Category category,
        @Size(max = 200) String location,
        @Size(max = 20) String status,
        CustodyStatus custody,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        CatalogSort sort,
        @Min(0) @Max(10000) Integer page,
        @Min(1) @Max(100) Integer pageSize) {
    public CatalogQueryDto {
        q = q == null ? "" : q.strip();
        location = location == null ? "" : location.strip();
        status = status == null || status.isBlank() ? null : status.strip();
        sort = sort == null ? CatalogSort.RECENT : sort;
        page = page == null ? 0 : page;
        pageSize = pageSize == null ? 20 : pageSize;
    }
    @AssertTrue(message = "dateFrom no puede ser posterior a dateTo")
    public boolean isDateRangeValid() {
        return dateFrom == null || dateTo == null || !dateFrom.isAfter(dateTo);
    }
}
