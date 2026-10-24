package com.foodieexpress.orderservice.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        String sort) {

    public PageResponse {
        items = List.copyOf(items);
    }

    public static <S, T> PageResponse<T> from(Page<S> page, String sort, java.util.function.Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                sort);
    }
}
