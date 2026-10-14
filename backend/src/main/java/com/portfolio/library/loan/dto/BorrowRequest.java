package com.portfolio.library.loan.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/loans}.
 *
 * @param bookId   the book to borrow (required)
 * @param memberId who the loan is for. Members leave it empty (they always borrow for themselves).
 *                 Librarians MUST provide it - they lend to a member at the desk.
 */
public record BorrowRequest(@NotNull Long bookId, Long memberId) {
}
