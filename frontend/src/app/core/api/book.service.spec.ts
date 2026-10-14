import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BookService } from './book.service';

describe('BookService', () => {
  let service: BookService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(BookService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends only the filters that have a value', () => {
    service.list({ q: 'tolkien', genre: '', availableOnly: true, page: 0 }).subscribe();

    // expectOne with a predicate lets us inspect the query parameters.
    const req = http.expectOne((r) => r.url === '/api/books');
    expect(req.request.params.get('q')).toBe('tolkien');
    expect(req.request.params.has('genre')).toBeFalse();      // empty string skipped
    expect(req.request.params.get('availableOnly')).toBe('true');
    expect(req.request.params.get('page')).toBe('0');           // 0 is a real value, kept
    req.flush({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 });
  });

  it('deletes by id', () => {
    service.delete(42).subscribe();
    const req = http.expectOne('/api/books/42');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
