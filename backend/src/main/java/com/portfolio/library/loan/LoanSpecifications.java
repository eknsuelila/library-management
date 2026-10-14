package com.portfolio.library.loan;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Filters for the librarian's loan list ({@code GET /api/loans}). See {@code BookSpecifications} for how
 * Specifications work.
 *
 * <p>Filtering by status is interesting because status is not a column: we translate each status into the
 * same date conditions {@link Loan#statusOn} uses, so the database can do the filtering (and paging).
 */
final class LoanSpecifications {

    private LoanSpecifications() {
    }

    static Specification<Loan> hasStatus(LoanStatus status, LocalDate today) {
        return (root, query, cb) -> {
            if (status == null) {
                return null;
            }
            var returned = root.<LocalDate>get("returnedDate");
            var due = root.<LocalDate>get("dueDate");
            return switch (status) {
                case RETURNED -> cb.isNotNull(returned);
                // Not returned and due today or later.
                case ACTIVE -> cb.and(cb.isNull(returned), cb.greaterThanOrEqualTo(due, today));
                // Not returned and the due date has passed.
                case OVERDUE -> cb.and(cb.isNull(returned), cb.lessThan(due, today));
            };
        };
    }

    static Specification<Loan> forMember(Long memberId) {
        return (root, query, cb) -> memberId == null ? null : cb.equal(root.get("member").get("id"), memberId);
    }

    static Specification<Loan> forBook(Long bookId) {
        return (root, query, cb) -> bookId == null ? null : cb.equal(root.get("book").get("id"), bookId);
    }
}
