import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Loan, LoanStatus, Page } from '../models';
import { toParams } from './http-params';

/** Filters for the librarian's loan list (GET /api/loans). */
export interface LoanQuery {
  status?: LoanStatus | '';
  memberId?: number | null;
  bookId?: number | null;
  page?: number;
  size?: number;
  sort?: string;
}

/** Typed wrapper around the /api/loans endpoints (see BookService for general notes). */
@Injectable({ providedIn: 'root' })
export class LoanService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/loans';

  /**
   * Borrow a book. Members omit memberId (they borrow for themselves); librarians must pass it.
   * Business-rule failures come back as 409 with a readable `message` (see errorMessage()).
   */
  borrow(bookId: number, memberId?: number): Observable<Loan> {
    return this.http.post<Loan>(this.baseUrl, memberId ? { bookId, memberId } : { bookId });
  }

  returnLoan(id: number): Observable<Loan> {
    return this.http.post<Loan>(`${this.baseUrl}/${id}/return`, {});
  }

  renew(id: number): Observable<Loan> {
    return this.http.post<Loan>(`${this.baseUrl}/${id}/renew`, {});
  }

  /** The logged-in user's own loans, newest first. */
  mine(): Observable<Loan[]> {
    return this.http.get<Loan[]>(`${this.baseUrl}/me`);
  }

  /** Librarian only: every loan, filtered and paged. */
  list(query: LoanQuery = {}): Observable<Page<Loan>> {
    return this.http.get<Page<Loan>>(this.baseUrl, { params: toParams(query) });
  }
}
