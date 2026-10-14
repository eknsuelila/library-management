/**
 * TypeScript interfaces that mirror the JSON exchanged with the Spring Boot API.
 *
 * Every interface here corresponds to a Java DTO record on the backend (named in each comment). Keeping them in
 * one file means that when a DTO changes, the TypeScript compiler points at every component that needs updating.
 * Interfaces and type aliases have zero runtime cost: they disappear when TypeScript compiles to JavaScript.
 *
 * Dates arrive as ISO strings ("2026-10-14"), and money (BigDecimal on the server) arrives as a JSON number.
 */

/** Java enum `Role`. */
export type Role = 'LIBRARIAN' | 'MEMBER';

/** Java `UserResponse`. */
export interface User {
  id: number;
  name: string;
  email: string;
  role: Role;
}

/** Response of POST /api/auth/login and /api/auth/register (Java `AuthResponse`). */
export interface AuthResponse {
  token: string;
  user: User;
}

/** Java `BookResponse`. */
export interface Book {
  id: number;
  isbn: string;
  title: string;
  author: string;
  genre: string;
  publishedYear: number | null;
  totalCopies: number;
  availableCopies: number;
  description: string | null;
  coverUrl: string | null;
  /** Optimistic-locking version. Sent back when editing so the server can detect concurrent edits. */
  version: number;
}

/** Body for creating/updating a book (Java `BookRequest`): no id or availableCopies, the server owns those. */
export type BookRequest = Omit<Book, 'id' | 'availableCopies' | 'version'> & { version: number | null };

/** Java enum `LoanStatus`. Computed by the server from the dates; never stored. */
export type LoanStatus = 'ACTIVE' | 'OVERDUE' | 'RETURNED';

export const LOAN_STATUSES: LoanStatus[] = ['ACTIVE', 'OVERDUE', 'RETURNED'];

/** Java `LoanResponse`. */
export interface Loan {
  id: number;
  bookId: number;
  bookTitle: string;
  bookAuthor: string;
  bookIsbn: string;
  bookCoverUrl: string | null;
  memberId: number;
  memberName: string;
  memberEmail: string;
  borrowedDate: string;
  dueDate: string;
  returnedDate: string | null;
  renewCount: number;
  status: LoanStatus;
  /** Days left until due (negative = days overdue); null once returned. */
  daysUntilDue: number | null;
  /** Recorded fine (returned) or fine accrued so far (overdue), in dollars. */
  fine: number;
  canRenew: boolean;
}

/** Java `MemberResponse`. */
export interface Member {
  id: number;
  name: string;
  email: string;
  joinedAt: string;
  activeLoans: number;
  overdueLoans: number;
  totalLoans: number;
}

export interface BookBorrowCount {
  bookId: number;
  title: string;
  author: string;
  loans: number;
}

export interface MonthlyCount {
  /** "2026-10" */
  month: string;
  count: number;
}

/** Response of GET /api/stats (Java `StatsResponse`). */
export interface Stats {
  totalTitles: number;
  totalCopies: number;
  copiesOnLoan: number;
  overdueLoans: number;
  members: number;
  finesOutstanding: number;
  topBorrowed: BookBorrowCount[];
  loansPerMonth: MonthlyCount[];
}

/** Generic page of results; mirrors the Java `PageResponse<T>` record. */
export interface Page<T> {
  content: T[];
  /** zero-based page index */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Shape of every error body produced by the backend's GlobalExceptionHandler (Java `ApiError`). */
export interface ApiError {
  status: number;
  error: string;
  message: string;
  fieldErrors: Record<string, string>;
}
