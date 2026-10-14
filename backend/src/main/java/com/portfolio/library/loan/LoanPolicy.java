package com.portfolio.library.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The library's lending rules as named constants plus the fine calculation.
 *
 * <p>Keeping the numbers in ONE place (instead of scattering "14" and "3" around the code) makes them
 * self-documenting and easy to change. In a bigger system these could come from configuration or a database
 * table so that librarians could edit them without a deployment.
 *
 * <p>The class is {@code final} with a private constructor: it is a holder of constants and pure functions,
 * never instantiated.
 */
public final class LoanPolicy {

    /** A loan (and each renewal) lasts two weeks. */
    public static final int LOAN_PERIOD_DAYS = 14;

    /** A member may hold at most this many books at once (overdue ones included). */
    public static final int MAX_ACTIVE_LOANS = 3;

    /** How many times one loan may be renewed. */
    public static final int MAX_RENEWALS = 1;

    /**
     * $0.25 for each day a book is returned late.
     *
     * <p><b>Why {@link BigDecimal} for money?</b> {@code double} is binary floating point and cannot represent
     * most decimal fractions exactly: {@code 0.1 + 0.2 == 0.30000000000000004}. Summed over thousands of fines
     * the cents drift. {@code BigDecimal} stores exact decimal digits, and its constructor takes a String
     * ({@code "0.25"}) precisely so that no inexact double is ever involved. The column is NUMERIC(8,2) to match.
     */
    public static final BigDecimal FINE_PER_DAY = new BigDecimal("0.25");

    private LoanPolicy() {
    }

    /**
     * The fine for a book that was due on {@code dueDate} and returned (or evaluated) on {@code date}.
     * Returning on the due date itself is on time.
     *
     * <p>Examples: due Oct 10, returned Oct 10 -> $0.00; returned Oct 14 -> 4 days late -> $1.00.
     *
     * @return the fine with exactly 2 decimal places, never negative
     */
    public static BigDecimal fineFor(LocalDate dueDate, LocalDate date) {
        long daysLate = ChronoUnit.DAYS.between(dueDate, date);
        if (daysLate <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return FINE_PER_DAY.multiply(BigDecimal.valueOf(daysLate)).setScale(2, RoundingMode.HALF_UP);
    }
}
