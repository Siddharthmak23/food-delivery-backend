package com.foodieexpress.deliveryservice.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        String sort) {

    public static <T> PageResponse<T> from(Page<T> page, String sort) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), sort);
    }

    public <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(
                items.stream().map(mapper).toList(), page, size, totalItems, totalPages, sort);
    }
}
