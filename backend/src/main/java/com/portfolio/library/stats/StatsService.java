package com.portfolio.library.stats;

import com.portfolio.library.book.BookRepository;
import com.portfolio.library.loan.LoanRepository;
import com.portfolio.library.stats.dto.MonthlyCount;
import com.portfolio.library.stats.dto.StatsResponse;
import com.portfolio.library.user.Role;
import com.portfolio.library.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregates numbers for the librarian dashboard. Read-only: it never changes data.
 *
 * <p>Counting and summing happen in the database (COUNT/SUM queries), which is far cheaper than loading
 * every row into Java. The only Java-side aggregation is bucketing loans into months, because the month
 * functions differ between H2 and PostgreSQL and the date range is small.
 */
@Service
@Transactional(readOnly = true)
public class StatsService {

    /** How many months the "loans per month" chart covers, including the current month. */
    static final int MONTHS_IN_CHART = 6;
    static final int TOP_BOOKS = 5;

    private final BookRepository books;
    private final LoanRepository loans;
    private final UserRepository users;
    private final Clock clock;

    public StatsService(BookRepository books, LoanRepository loans, UserRepository users, Clock clock) {
        this.books = books;
        this.loans = loans;
        this.users = users;
        this.clock = clock;
    }

    public StatsResponse stats() {
        LocalDate today = LocalDate.now(clock);

        // Recorded fines (returned loans) + fines still growing on overdue loans.
        BigDecimal accruing = loans.findByReturnedDateIsNullAndDueDateBefore(today).stream()
                .map(loan -> loan.currentFine(today))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal finesOutstanding = loans.sumRecordedFines().add(accruing).setScale(2);

        return new StatsResponse(
                books.count(),
                books.sumTotalCopies(),
                loans.countByReturnedDateIsNull(),
                loans.countByReturnedDateIsNullAndDueDateBefore(today),
                users.countByRole(Role.MEMBER),
                finesOutstanding,
                loans.findMostBorrowed(PageRequest.of(0, TOP_BOOKS)),
                loansPerMonth(today));
    }

    /**
     * Loans per month for the last {@link #MONTHS_IN_CHART} months, oldest first. Months with no loans are
     * included with a count of 0 so the chart's x-axis has no gaps.
     */
    private List<MonthlyCount> loansPerMonth(LocalDate today) {
        YearMonth current = YearMonth.from(today);
        YearMonth first = current.minusMonths(MONTHS_IN_CHART - 1L);

        // TreeMap keeps the keys sorted (chronologically for YearMonth).
        Map<YearMonth, Long> counts = new TreeMap<>();
        for (int i = 0; i < MONTHS_IN_CHART; i++) {
            counts.put(first.plusMonths(i), 0L);
        }
        for (LocalDate date : loans.findBorrowedDatesSince(first.atDay(1))) {
            // computeIfPresent ignores dates outside the window (e.g. seeded data dated in the future).
            counts.computeIfPresent(YearMonth.from(date), (month, count) -> count + 1);
        }
        return counts.entrySet().stream()
                .map(e -> new MonthlyCount(e.getKey().toString(), e.getValue()))
                .toList();
    }
}
