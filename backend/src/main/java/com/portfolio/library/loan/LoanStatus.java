package com.portfolio.library.loan;

/**
 * The state of a loan. It is <b>computed</b>, never stored (there is no status column), see
 * {@link Loan#statusOn(java.time.LocalDate)}:
 *
 * <ul>
 *   <li>{@link #ACTIVE}   - not returned yet, and today is on or before the due date;</li>
 *   <li>{@link #OVERDUE}  - not returned yet, and today is after the due date;</li>
 *   <li>{@link #RETURNED} - the book came back ({@code returnedDate} is set).</li>
 * </ul>
 *
 * <p>Why compute it? ACTIVE silently turns into OVERDUE at midnight without anyone touching the row. A stored
 * status would need a nightly batch job to stay correct, and would be wrong between the deadline and the
 * job's next run. Deriving it from {@code dueDate}, {@code returnedDate} and today's date (from the injected
 * {@code Clock}) is always correct and trivially testable with a fixed clock.
 */
public enum LoanStatus {
    ACTIVE,
    OVERDUE,
    RETURNED
}
