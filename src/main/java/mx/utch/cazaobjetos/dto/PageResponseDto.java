package mx.utch.cazaobjetos.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponseDto<T>(List<T> items, long total, int page, int pageSize,
                                 int totalPages, boolean hasNext, boolean hasPrevious) {
    public static <T> PageResponseDto<T> from(Page<T> page) {
        return new PageResponseDto<>(page.getContent(), page.getTotalElements(), page.getNumber(),
                page.getSize(), page.getTotalPages(), page.hasNext(), page.hasPrevious());
    }
}
