package com.portfolio.library.loan;

import com.portfolio.library.book.Book;
import com.portfolio.library.book.BookRepository;
import com.portfolio.library.common.BadRequestException;
import com.portfolio.library.common.ConflictException;
import com.portfolio.library.common.PageResponse;
import com.portfolio.library.common.ResourceNotFoundException;
import com.portfolio.library.loan.dto.BorrowRequest;
import com.portfolio.library.loan.dto.LoanResponse;
import com.portfolio.library.user.Role;
import com.portfolio.library.user.User;
import com.portfolio.library.user.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static com.portfolio.library.loan.LoanSpecifications.*;

/**
 * The heart of the application: borrowing, renewing and returning books, with every lending rule enforced.
 *
 * <p><b>Request flow</b> (borrowing): {@code LoanController.borrow} -> {@link #borrow} -> checks the rules using
 * {@link LoanRepository} queries -> updates the {@link Book}'s stock and inserts a {@link Loan} -> the whole
 * thing commits as ONE transaction -> {@link LoanResponse} is returned as JSON.
 *
 * <p><b>{@code @Transactional} on the class</b> applies to every public method: each runs in a database
 * transaction that commits when the method returns normally and rolls back if a RuntimeException escapes. That
 * is what makes "decrement the stock AND insert the loan" atomic: either both happen or neither does.
 *
 * <p><b>Who may do what:</b>
 * <ul>
 *   <li>Members borrow for themselves, and see/renew/return only their own loans (others' loans are 404).</li>
 *   <li>Librarians lend to any member (must pass {@code memberId}) and can see/renew/return every loan.</li>
 * </ul>
 */
@Service
@Transactional
public class LoanService {

    private final LoanRepository loans;
    private final BookRepository books;
    private final UserRepository users;
    private final Clock clock;

    public LoanService(LoanRepository loans, BookRepository books, UserRepository users, Clock clock) {
        this.loans = loans;
        this.books = books;
        this.users = users;
        this.clock = clock;
    }

    /**
     * Lends a book. Rules are checked in a deliberate order, from "about the member" to "about the book", so
     * the user sees the most actionable message first:
     * <ol>
     *   <li>no overdue loans outstanding (return those first);</li>
     *   <li>fewer than {@link LoanPolicy#MAX_ACTIVE_LOANS} books currently held;</li>
     *   <li>not already holding a copy of this same book;</li>
     *   <li>at least one copy on the shelf.</li>
     * </ol>
     * Each violation throws {@link ConflictException} (HTTP 409) with a user-facing message.
     *
     * <p><b>Concurrency:</b> two requests for the last copy can both pass rule 4 at the same moment. The
     * {@code @Version} column on {@link Book} makes the second UPDATE fail; {@code saveAndFlush} forces that
     * UPDATE to run now (instead of at commit), the exception rolls back this whole transaction - including
     * the new loan row - and the client receives a 409 "changed by someone else, try again".
     *
     * @param caller the logged-in user (member or librarian)
     */
    public LoanResponse borrow(User caller, BorrowRequest request) {
        User member = resolveBorrower(caller, request.memberId());
        Book book = books.findById(request.bookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book " + request.bookId() + " not found"));
        LocalDate today = today();
        boolean self = member.getId().equals(caller.getId());

        if (loans.existsByMemberIdAndReturnedDateIsNullAndDueDateBefore(member.getId(), today)) {
            throw new ConflictException(self
                    ? "You have an overdue loan. Please return it before borrowing another book."
                    : member.getName() + " has an overdue loan and cannot borrow until it is returned.");
        }
        if (loans.countByMemberIdAndReturnedDateIsNull(member.getId()) >= LoanPolicy.MAX_ACTIVE_LOANS) {
            throw new ConflictException("Borrowing limit reached: a member can have at most "
                    + LoanPolicy.MAX_ACTIVE_LOANS + " books at a time.");
        }
        if (loans.existsByMemberIdAndBookIdAndReturnedDateIsNull(member.getId(), book.getId())) {
            throw new ConflictException((self ? "You already have" : member.getName() + " already has")
                    + " a copy of \"" + book.getTitle() + "\" on loan.");
        }
        if (book.getAvailableCopies() <= 0) {
            throw new ConflictException("No copies of \"" + book.getTitle() + "\" are available right now.");
        }

        book.checkOutCopy();
        books.saveAndFlush(book);          // version check happens HERE (see method comment)
        Loan loan = loans.save(new Loan(book, member, today));
        return LoanResponse.from(loan, today);
    }

    /**
     * Returns a book: closes the loan, records the late fine ({@link LoanPolicy#fineFor}) and puts the copy
     * back on the shelf. A second return of the same loan is rejected with 409 (e.g. a double click).
     */
    public LoanResponse returnLoan(User caller, Long loanId) {
        Loan loan = findAccessible(caller, loanId);
        LocalDate today = today();
        loan.markReturned(today);
        Book book = loan.getBook();
        book.returnCopy();
        books.saveAndFlush(book);
        return LoanResponse.from(loan, today);
    }

    /**
     * Extends the due date by {@link LoanPolicy#LOAN_PERIOD_DAYS} days - at most
     * {@link LoanPolicy#MAX_RENEWALS} time(s), and never for an overdue loan (see {@link Loan#renew}).
     *
     * <p>No explicit save is needed: the loan is a "managed" entity loaded in this transaction, so Hibernate
     * notices the changed fields ("dirty checking") and issues the UPDATE automatically at commit.
     */
    public LoanResponse renew(User caller, Long loanId) {
        Loan loan = findAccessible(caller, loanId);
        LocalDate today = today();
        loan.renew(today);
        return LoanResponse.from(loan, today);
    }

    /** All loans of the caller, newest first (current and past). Powers the "My loans" page. */
    @Transactional(readOnly = true)
    public List<LoanResponse> myLoans(User caller) {
        LocalDate today = today();
        return loans.findByMemberIdOrderByBorrowedDateDescIdDesc(caller.getId()).stream()
                .map(loan -> LoanResponse.from(loan, today))
                .toList();
    }

    /** Librarian view: every loan, optionally filtered by status, member or book, one page at a time. */
    @Transactional(readOnly = true)
    public PageResponse<LoanResponse> search(LoanStatus status, Long memberId, Long bookId, Pageable pageable) {
        LocalDate today = today();
        var spec = hasStatus(status, today).and(forMember(memberId)).and(forBook(bookId));
        return PageResponse.from(loans.findAll(spec, pageable).map(loan -> LoanResponse.from(loan, today)));
    }

    // ----- helpers -----

    /**
     * Decides whom a new loan is for.
     * <ul>
     *   <li>Member: always themselves. Passing someone else's id is an authorization error (403).</li>
     *   <li>Librarian: must name a member (400 if missing); the target must exist (404) and be a MEMBER (400).</li>
     * </ul>
     */
    private User resolveBorrower(User caller, Long memberId) {
        if (!caller.isLibrarian()) {
            if (memberId != null && !memberId.equals(caller.getId())) {
                throw new AccessDeniedException("Members can only borrow books for themselves");
            }
            return caller;
        }
        if (memberId == null) {
            throw new BadRequestException("memberId is required when a librarian lends a book");
        }
        User member = users.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member " + memberId + " not found"));
        if (member.getRole() != Role.MEMBER) {
            throw new BadRequestException("Books can only be lent to members");
        }
        return member;
    }

    /**
     * Loads a loan the caller is allowed to act on. Librarians: any loan. Members: only their own; for
     * anybody else's loan the query finds nothing and we answer 404, so ids of other members' loans can't
     * even be confirmed to exist.
     */
    private Loan findAccessible(User caller, Long loanId) {
        var loan = caller.isLibrarian()
                ? loans.findById(loanId)
                : loans.findByIdAndMemberId(loanId, caller.getId());
        return loan.orElseThrow(() -> new ResourceNotFoundException("Loan " + loanId + " not found"));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
