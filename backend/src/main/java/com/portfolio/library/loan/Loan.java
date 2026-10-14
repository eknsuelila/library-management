package com.portfolio.library.loan;

import com.portfolio.library.book.Book;
import com.portfolio.library.common.ConflictException;
import com.portfolio.library.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One borrowing of one book by one member: from the day it was borrowed until the day it comes back.
 *
 * <p><b>Where it fits:</b> created by {@code LoanService.borrow}, changed by {@code renew} and {@code returnLoan},
 * read by the "My loans" page, the librarian's loan list and the statistics. Rows are kept after return so
 * the library has a borrowing history (used for "most borrowed books" and "loans per month").
 *
 * <p>This is a "rich" entity: the state-changing rules that only depend on the loan itself
 * ({@link #renew}, {@link #markReturned}, {@link #statusOn}) live here, next to the data they protect.
 * Rules that need other data (the member's other loans, the book's stock) live in {@code LoanService}.
 */
@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * {@code @ManyToOne}: many loans point at one book (the {@code book_id} foreign key).
     * {@code FetchType.LAZY}: the book is only loaded from the database when first accessed, not every time a
     * loan is read. Queries that know they need it use an entity graph / join fetch to avoid "N+1" queries.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    /** The member who has (or had) the book. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private User member;

    @Column(name = "borrowed_date", nullable = false)
    private LocalDate borrowedDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** {@code null} while the book is still out. */
    @Column(name = "returned_date")
    private LocalDate returnedDate;

    @Column(name = "renew_count", nullable = false)
    private int renewCount;

    /**
     * Late fee charged when the book was returned. {@code precision}/{@code scale} mirror NUMERIC(8,2).
     * See {@link LoanPolicy#FINE_PER_DAY} for why money is a {@link BigDecimal}.
     */
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal fine = BigDecimal.ZERO.setScale(2);

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Required by JPA. */
    protected Loan() {
    }

    /** Starts a loan today: due {@link LoanPolicy#LOAN_PERIOD_DAYS} days later. */
    public Loan(Book book, User member, LocalDate borrowedDate) {
        this.book = book;
        this.member = member;
        this.borrowedDate = borrowedDate;
        this.dueDate = borrowedDate.plusDays(LoanPolicy.LOAN_PERIOD_DAYS);
    }

    // ----- Domain behaviour -----

    /**
     * The status of this loan on the given day (normally "today" from the injected Clock).
     * Not stored in the database - see {@link LoanStatus} for why.
     */
    public LoanStatus statusOn(LocalDate today) {
        if (returnedDate != null) {
            return LoanStatus.RETURNED;
        }
        return today.isAfter(dueDate) ? LoanStatus.OVERDUE : LoanStatus.ACTIVE;
    }

    public boolean isReturned() {
        return returnedDate != null;
    }

    /** True if a renewal is allowed today (used both to enforce the rule and to show/hide the button). */
    public boolean canRenewOn(LocalDate today) {
        return statusOn(today) == LoanStatus.ACTIVE && renewCount < LoanPolicy.MAX_RENEWALS;
    }

    /**
     * Extends the due date by another loan period.
     *
     * @throws ConflictException if the loan is returned, overdue, or was already renewed
     */
    public void renew(LocalDate today) {
        LoanStatus status = statusOn(today);
        if (status == LoanStatus.RETURNED) {
            throw new ConflictException("This loan has already been returned");
        }
        if (status == LoanStatus.OVERDUE) {
            throw new ConflictException("Overdue loans cannot be renewed. Please return the book.");
        }
        if (renewCount >= LoanPolicy.MAX_RENEWALS) {
            throw new ConflictException("This loan has already been renewed the maximum number of times ("
                    + LoanPolicy.MAX_RENEWALS + ")");
        }
        // Extend from the current due date (not from today), so renewing early never shortens the loan.
        dueDate = dueDate.plusDays(LoanPolicy.LOAN_PERIOD_DAYS);
        renewCount++;
    }

    /**
     * Closes the loan and records the late fee.
     *
     * @throws ConflictException if it was already returned (e.g. a double click sent two requests)
     */
    public void markReturned(LocalDate today) {
        if (isReturned()) {
            throw new ConflictException("This loan has already been returned");
        }
        returnedDate = today;
        fine = LoanPolicy.fineFor(dueDate, today);
    }

    /**
     * The fine to DISPLAY: the recorded fine for a returned loan, or the fine accrued so far for an
     * overdue one (what the member would owe if they returned it today).
     */
    public BigDecimal currentFine(LocalDate today) {
        return isReturned() ? fine : LoanPolicy.fineFor(dueDate, today);
    }

    // ----- Getters (no public setters: state changes only through the methods above) -----

    public Long getId() { return id; }
    public Book getBook() { return book; }
    public User getMember() { return member; }
    public LocalDate getBorrowedDate() { return borrowedDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getReturnedDate() { return returnedDate; }
    public int getRenewCount() { return renewCount; }
    public BigDecimal getFine() { return fine; }
    public Instant getCreatedAt() { return createdAt; }
}
