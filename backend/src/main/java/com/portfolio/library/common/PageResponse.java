package com.portfolio.library.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A stable, frontend-friendly page of results, e.g.
 * {@code {"content":[...], "page":0, "size":12, "totalElements":25, "totalPages":3}}.
 *
 * <p>Why not return Spring's {@link Page} directly? Its JSON shape is an internal detail that has changed
 * between Spring versions (Spring now even logs a warning when you serialize it). Our own record is a
 * contract we control, and it mirrors the {@code Page<T>} interface in the Angular {@code models.ts}.
 *
 * @param page zero-based page index
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    /** Copies the numbers we care about out of a Spring Data {@link Page}. */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
