package com.portfolio.library.loan;

import com.portfolio.library.member.dto.MemberLoanCount;
import com.portfolio.library.stats.dto.BookBorrowCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link Loan}. Most methods are derived queries: Spring reads the method name, e.g.
 * {@code countByMemberIdAndReturnedDateIsNull} becomes
 * {@code SELECT count(*) FROM loans WHERE member_id = ? AND returned_date IS NULL}.
 *
 * <p>"ReturnedDateIsNull" is how we say "still out" (ACTIVE or OVERDUE) - see {@link LoanStatus}.
 */
public interface LoanRepository extends JpaRepository<Loan, Long>, JpaSpecificationExecutor<Loan> {

    // ----- Business-rule checks used when borrowing -----

    /** How many books the member currently holds (rule: max 3). */
    long countByMemberIdAndReturnedDateIsNull(Long memberId);

    /** Does the member already have this exact book? (rule: no duplicate concurrent loans) */
    boolean existsByMemberIdAndBookIdAndReturnedDateIsNull(Long memberId, Long bookId);

    /** Does the member have any loan still out past its due date? (rule: overdue blocks borrowing) */
    boolean existsByMemberIdAndReturnedDateIsNullAndDueDateBefore(Long memberId, LocalDate today);

    /** Copies of a book currently on loan (rules: can't delete it / can't shrink totalCopies below this). */
    long countByBookIdAndReturnedDateIsNull(Long bookId);

    // ----- Reading loans -----

    /**
     * Finds a loan only if it belongs to the given member. Used for members so that someone else's loan is
     * simply "not found" (404) - they can't even learn that the id exists.
     */
    Optional<Loan> findByIdAndMemberId(Long id, Long memberId);

    /**
     * All loans of one member, newest first. {@code @EntityGraph} tells Hibernate to load the related book in
     * the SAME query (joins). Without it, mapping 20 loans to JSON would fire 20 extra "select book" queries,
     * the classic N+1 problem.
     */
    @EntityGraph(attributePaths = {"book", "member"})
    List<Loan> findByMemberIdOrderByBorrowedDateDescIdDesc(Long memberId);

    /**
     * Overrides the inherited {@code findAll(Specification, Pageable)} only to add the entity graph, so the
     * librarian's paged loan list loads book and member with a join instead of N+1 queries.
     */
    @Override
    @EntityGraph(attributePaths = {"book", "member"})
    Page<Loan> findAll(Specification<Loan> spec, Pageable pageable);

    /**
     * Deletes the (returned) loan history of a book. {@code @Modifying} marks a JPQL query that changes data;
     * it must run inside a transaction. Used just before deleting a book, since loans.book_id is a foreign key.
     */
    @Modifying
    @Query("delete from Loan l where l.book.id = :bookId")
    void deleteByBookId(Long bookId);

    // ----- Statistics -----

    long countByReturnedDateIsNull();

    long countByReturnedDateIsNullAndDueDateBefore(LocalDate today);

    /** Overdue loans (to add up fines that are still accruing). */
    List<Loan> findByReturnedDateIsNullAndDueDateBefore(LocalDate today);

    /** Sum of fines recorded on returned loans. */
    @Query("select coalesce(sum(l.fine), 0) from Loan l")
    BigDecimal sumRecordedFines();

    /**
     * Most borrowed books of all time. "select new ..." is a JPQL constructor expression: each result row is
     * turned directly into a {@link BookBorrowCount} record. Pass {@code PageRequest.of(0, 5)} to get the top 5.
     */
    @Query("""
            select new com.portfolio.library.stats.dto.BookBorrowCount(b.id, b.title, b.author, count(l))
            from Loan l join l.book b
            group by b.id, b.title, b.author
            order by count(l) desc, b.title asc
            """)
    List<BookBorrowCount> findMostBorrowed(Pageable pageable);

    /** Borrow dates from {@code since} on; the service buckets them into months (including empty months). */
    @Query("select l.borrowedDate from Loan l where l.borrowedDate >= :since")
    List<LocalDate> findBorrowedDatesSince(LocalDate since);

    // ----- Per-member counters for the librarian's member list -----

    @Query("""
            select new com.portfolio.library.member.dto.MemberLoanCount(l.member.id, count(l))
            from Loan l where l.member.id in :memberIds and l.returnedDate is null
            group by l.member.id
            """)
    List<MemberLoanCount> countActiveByMember(List<Long> memberIds);

    @Query("""
            select new com.portfolio.library.member.dto.MemberLoanCount(l.member.id, count(l))
            from Loan l where l.member.id in :memberIds and l.returnedDate is null and l.dueDate < :today
            group by l.member.id
            """)
    List<MemberLoanCount> countOverdueByMember(List<Long> memberIds, LocalDate today);

    @Query("""
            select new com.portfolio.library.member.dto.MemberLoanCount(l.member.id, count(l))
            from Loan l where l.member.id in :memberIds
            group by l.member.id
            """)
    List<MemberLoanCount> countAllByMember(List<Long> memberIds);
}
