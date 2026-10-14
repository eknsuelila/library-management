package com.portfolio.library.loan.dto;

import com.portfolio.library.loan.Loan;
import com.portfolio.library.loan.LoanStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * JSON representation of a {@link Loan}, flattened with the book and member details the UI needs so the
 * frontend doesn't have to make extra requests.
 *
 * <p>Several fields are DERIVED for "today" (the injected Clock) rather than stored:
 *
 * @param status       ACTIVE / OVERDUE / RETURNED (see {@link LoanStatus})
 * @param daysUntilDue days left until the due date; negative when overdue; {@code null} once returned
 * @param fine         recorded fine if returned, otherwise the fine accrued so far (0.00 if not late)
 * @param canRenew     whether the renew button should be enabled right now
 */
public record LoanResponse(
        Long id,
        Long bookId,
        String bookTitle,
        String bookAuthor,
        String bookIsbn,
        String bookCoverUrl,
        Long memberId,
        String memberName,
        String memberEmail,
        LocalDate borrowedDate,
        LocalDate dueDate,
        LocalDate returnedDate,
        int renewCount,
        LoanStatus status,
        Integer daysUntilDue,
        BigDecimal fine,
        boolean canRenew) {

    /**
     * Maps an entity to the DTO. Must be called inside a transaction because {@code book} and {@code member}
     * are lazy associations.
     *
     * @param today "today" according to the application Clock
     */
    public static LoanResponse from(Loan loan, LocalDate today) {
        var book = loan.getBook();
        var member = loan.getMember();
        Integer daysUntilDue = loan.isReturned()
                ? null
                : (int) ChronoUnit.DAYS.between(today, loan.getDueDate());
        return new LoanResponse(
                loan.getId(),
                book.getId(), book.getTitle(), book.getAuthor(), book.getIsbn(), book.getCoverUrl(),
                member.getId(), member.getName(), member.getEmail(),
                loan.getBorrowedDate(), loan.getDueDate(), loan.getReturnedDate(), loan.getRenewCount(),
                loan.statusOn(today), daysUntilDue, loan.currentFine(today), loan.canRenewOn(today));
    }
}
