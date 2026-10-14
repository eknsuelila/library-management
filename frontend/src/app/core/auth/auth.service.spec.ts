import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService, TOKEN_KEY } from './auth.service';

/**
 * HttpTestingController intercepts HttpClient calls so no real network request is made. Each test asserts on the
 * request the service sent, then "flushes" a fake response back to it.
 *
 * logout() navigates to /login, so the test router needs a matching route (an empty `children` array is enough).
 */
describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: 'login', children: [] }])],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify()); // fails the test if an unexpected request was made

  it('starts logged out', () => {
    expect(service.isLoggedIn()).toBeFalse();
    expect(service.role()).toBeNull();
    expect(service.homeUrl()).toBe('/login');
  });

  it('stores the session after login and exposes the role', () => {
    service.login('librarian@example.com', 'password123').subscribe();

    const req = http.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'librarian@example.com', password: 'password123' });
    req.flush({ token: 'abc.def.ghi', user: { id: 1, name: 'Lena', email: 'librarian@example.com', role: 'LIBRARIAN' } });

    expect(service.isLoggedIn()).toBeTrue();
    expect(service.isLibrarian()).toBeTrue();
    expect(service.isMember()).toBeFalse();
    expect(service.token).toBe('abc.def.ghi');
    expect(service.homeUrl()).toBe('/dashboard');
    expect(localStorage.getItem(TOKEN_KEY)).toBe('abc.def.ghi');
  });

  it('sends members to the catalog and clears everything on logout', () => {
    service.login('member@example.com', 'x').subscribe();
    http.expectOne('/api/auth/login').flush({ token: 't', user: { id: 2, name: 'Maya', email: 'member@example.com', role: 'MEMBER' } });
    expect(service.homeUrl()).toBe('/catalog');

    service.logout();

    expect(service.isLoggedIn()).toBeFalse();
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
  });
});
