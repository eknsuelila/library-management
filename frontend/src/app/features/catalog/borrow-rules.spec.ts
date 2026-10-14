import { borrowBlockReason } from './borrow-rules';

/** Pure function => plain Jasmine tests, no TestBed needed. */
describe('borrowBlockReason', () => {
  const book = { id: 1, availableCopies: 2 };

  it('allows borrowing when no rule is broken', () => {
    expect(borrowBlockReason(book, [])).toBeNull();
  });

  it('blocks when there are no copies left', () => {
    expect(borrowBlockReason({ id: 1, availableCopies: 0 }, [])).toBe('No copies available');
  });

  it('blocks a second copy of the same book', () => {
    expect(borrowBlockReason(book, [{ bookId: 1, status: 'ACTIVE' }])).toBe('You already have this book');
  });

  it('blocks at the 3-book limit', () => {
    const loans = [2, 3, 4].map((bookId) => ({ bookId, status: 'ACTIVE' as const }));
    expect(borrowBlockReason(book, loans)).toContain('Limit reached');
  });

  it('reports an overdue loan before anything else', () => {
    const loans = [{ bookId: 1, status: 'OVERDUE' as const }];
    expect(borrowBlockReason({ id: 1, availableCopies: 0 }, loans)).toBe('Return your overdue book first');
  });
});
