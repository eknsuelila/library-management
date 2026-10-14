import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Book, BookRequest, Page } from '../models';
import { toParams } from './http-params';

/** Query options for GET /api/books. All optional. */
export interface BookQuery {
  q?: string;
  genre?: string;
  availableOnly?: boolean;
  page?: number;
  size?: number;
  /** Spring Data format "property,direction", e.g. "title,asc". */
  sort?: string;
}

/**
 * Typed wrapper around the /api/books endpoints.
 *
 * Components never build URLs themselves; they call these methods. HttpClient returns *cold* Observables:
 * nothing is sent until something calls `.subscribe()`.
 *
 * URLs are relative ("/api/..."): in development the Angular dev server proxies them to the backend on
 * http://localhost:8083 (proxy.conf.json); in Docker, nginx forwards them (nginx.conf).
 */
@Injectable({ providedIn: 'root' })
export class BookService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/books';

  list(query: BookQuery = {}): Observable<Page<Book>> {
    return this.http.get<Page<Book>>(this.baseUrl, { params: toParams(query) });
  }

  genres(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/genres`);
  }

  get(id: number): Observable<Book> {
    return this.http.get<Book>(`${this.baseUrl}/${id}`);
  }

  /** Librarian only (the server answers 403 otherwise). */
  create(body: BookRequest): Observable<Book> {
    return this.http.post<Book>(this.baseUrl, body);
  }

  /** Librarian only. Send the `version` you loaded so concurrent edits are detected (409). */
  update(id: number, body: BookRequest): Observable<Book> {
    return this.http.put<Book>(`${this.baseUrl}/${id}`, body);
  }

  /** Librarian only. 409 if copies are still on loan. */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
