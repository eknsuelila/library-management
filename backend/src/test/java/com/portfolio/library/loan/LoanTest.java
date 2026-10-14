package com.portfolio.library.loan;

import com.portfolio.library.book.Book;
import com.portfolio.library.user.Role;
import com.portfolio.library.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain JUnit tests (no mocks, no Spring) for the date logic on the {@link Loan} entity and {@link LoanPolicy}.
 * Because "today" is a parameter, we can test any day we like.
 */
class LoanTest {

    private final Loan loan = new Loan(
            new Book("9780547928227", "The Hobbit", "J.R.R. Tolkien", "Fantasy", 1),
            new User("Maya", "maya@example.com", "hash", Role.MEMBER),
            LocalDate.of(2026, 10, 1));                            // due 2026-10-15

    @Test
    void status_isComputedFromTheDates() {
        assertThat(loan.statusOn(LocalDate.of(2026, 10, 15))).isEqualTo(LoanStatus.ACTIVE);   // due date itself
        assertThat(loan.statusOn(LocalDate.of(2026, 10, 16))).isEqualTo(LoanStatus.OVERDUE);  // one day later

        loan.markReturned(LocalDate.of(2026, 10, 16));
        assertThat(loan.statusOn(LocalDate.of(2026, 12, 1))).isEqualTo(LoanStatus.RETURNED);
    }

    @Test
    void fine_is25CentsPerDayLate_withExactDecimals() {
        assertThat(LoanPolicy.fineFor(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15)))
                .isEqualTo(new BigDecimal("0.00"));
        assertThat(LoanPolicy.fineFor(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 25)))
                .isEqualTo(new BigDecimal("2.50"));
        // An overdue (not yet returned) loan shows the fine accrued so far.
        assertThat(loan.currentFine(LocalDate.of(2026, 10, 19))).isEqualTo(new BigDecimal("1.00"));
    }
}
