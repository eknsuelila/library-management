import { Book, Loan } from '../../core/models';

/** Must match LoanPolicy.MAX_ACTIVE_LOANS on the backend. */
export const MAX_ACTIVE_LOANS = 3;

/**
 * Explains why the logged-in member can NOT borrow a book right now, or returns null if they can.
 *
 * The catalog uses it to disable the "Borrow" button and show the reason underneath, so members aren't
 * invited to click a button that is bound to fail. The checks mirror LoanService.borrow on the server, in the
 * same order - but the server remains the authority: if anything changed in the meantime (e.g. another member
 * just took the last copy), the API still answers 409 and the catalog shows that message instead.
 *
 * Kept as a pure function (input -> output, no Angular) so it is trivial to unit-test.
 *
 * @param book        the book on the card
 * @param openLoans   the member's loans that are not returned yet (ACTIVE or OVERDUE)
 */
export function borrowBlockReason(
  book: Pick<Book, 'id' | 'availableCopies'>,
  openLoans: Pick<Loan, 'bookId' | 'status'>[],
): string | null {
  if (openLoans.some((l) => l.status === 'OVERDUE')) {
    return 'Return your overdue book first';
  }
  if (openLoans.length >= MAX_ACTIVE_LOANS) {
    return `Limit reached (${MAX_ACTIVE_LOANS} books)`;
  }
  if (openLoans.some((l) => l.bookId === book.id)) {
    return 'You already have this book';
  }
  if (book.availableCopies <= 0) {
    return 'No copies available';
  }
  return null;
}
