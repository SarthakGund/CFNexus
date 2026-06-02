package com.cfduel.rating.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Minimal pagination envelope returned by paginated profile endpoints. */
public record PagedResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PagedResponse<T> of(Page<S> page, List<T> items) {
        return new PagedResponse<>(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
