package com.portfolio.library.stats.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the librarian dashboard shows, computed in one request.
 *
 * @param totalTitles      number of books (titles) in the catalog
 * @param totalCopies      physical copies owned across all titles
 * @param copiesOnLoan     copies currently out (= loans not yet returned)
 * @param overdueLoans     loans past their due date and not yet returned
 * @param members          number of MEMBER accounts
 * @param finesOutstanding fines recorded on returned loans plus fines accruing on overdue loans. There is no
 *                         "fine paid" feature yet, so recorded fines are treated as unpaid.
 * @param topBorrowed      the 5 most borrowed books of all time
 * @param loansPerMonth    loans started in each of the last 6 months (oldest first, empty months included)
 */
public record StatsResponse(long totalTitles,
                            long totalCopies,
                            long copiesOnLoan,
                            long overdueLoans,
                            long members,
                            BigDecimal finesOutstanding,
                            List<BookBorrowCount> topBorrowed,
                            List<MonthlyCount> loansPerMonth) {
}
