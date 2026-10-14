import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Role } from '../models';
import { roleGuard } from './auth.guard';
import { TOKEN_KEY, USER_KEY } from './auth.service';

/**
 * Functional guards use inject(), so they must run inside an "injection context".
 * TestBed.runInInjectionContext() provides one.
 */
describe('roleGuard', () => {
  function loginAs(role: Role | null): void {
    localStorage.clear();
    if (role) {
      localStorage.setItem(TOKEN_KEY, 't');
      localStorage.setItem(USER_KEY, JSON.stringify({ id: 1, name: 'U', email: 'u@x.com', role }));
    }
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  }

  /** Runs the guard for a route that declares `data: { roles }`; returns true or the redirect URL. */
  function run(roles?: Role[]): true | string {
    const route = { data: roles ? { roles } : {} } as unknown as ActivatedRouteSnapshot;
    const result = TestBed.runInInjectionContext(() => roleGuard(route, {} as RouterStateSnapshot));
    if (result === true) return true;
    return TestBed.inject(Router).serializeUrl(result as UrlTree);
  }

  afterEach(() => localStorage.clear());

  it('sends anonymous users to /login', () => {
    loginAs(null);
    expect(run(['LIBRARIAN'])).toBe('/login');
  });

  it('lets a librarian into librarian pages', () => {
    loginAs('LIBRARIAN');
    expect(run(['LIBRARIAN'])).toBeTrue();
  });

  it('redirects a member away from librarian pages to the catalog', () => {
    loginAs('MEMBER');
    expect(run(['LIBRARIAN'])).toBe('/catalog');
  });

  it('redirects a librarian away from member-only pages to the dashboard', () => {
    loginAs('LIBRARIAN');
    expect(run(['MEMBER'])).toBe('/dashboard');
  });

  it('allows any logged-in user when the route declares no roles', () => {
    loginAs('MEMBER');
    expect(run()).toBeTrue();
  });
});
