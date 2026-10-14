import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthResponse, Role, User } from '../models';

/**
 * localStorage keys. Prefixed with "library." so they never clash with other apps served from the same origin
 * (e.g. other projects on localhost). The token survives page refreshes until the user logs out.
 */
export const TOKEN_KEY = 'library.token';
export const USER_KEY = 'library.user';

/**
 * Owns everything about "who is logged in and what role do they have".
 *
 * `@Injectable({ providedIn: 'root' })` registers ONE shared instance (a singleton) for the whole app; any
 * component or function can get it with `inject(AuthService)`.
 *
 * State is held in Angular *signals*. A signal is a reactive value: `signal(x)` creates one, calling it
 * (`currentUser()`) reads it, and `.set()` changes it. Any template or `computed()` that read the signal is
 * re-evaluated automatically when it changes - so the navbar switches between member and librarian links on
 * login/logout without any manual change-detection code.
 *
 * Security note: the role stored here only decides what the UI SHOWS. Every API call is still authorized by the
 * server (JWT + @PreAuthorize), so editing localStorage can't grant real permissions.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  // inject() is the modern alternative to constructor parameters for dependency injection.
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  /** Private writable signal; the rest of the app only gets the read-only view below. */
  private readonly _currentUser = signal<User | null>(this.readStoredUser());

  readonly currentUser = this._currentUser.asReadonly();

  /**
   * computed() derives a signal from other signals. It is cached and only recalculated when a signal it reads
   * (here `_currentUser`) changes.
   */
  readonly isLoggedIn = computed(() => this._currentUser() !== null);
  readonly role = computed<Role | null>(() => this._currentUser()?.role ?? null);
  readonly isLibrarian = computed(() => this.role() === 'LIBRARIAN');
  readonly isMember = computed(() => this.role() === 'MEMBER');

  /** The raw JWT, read by the interceptor for each request. */
  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  /** True if the current user has one of the given roles (used by roleGuard). */
  hasAnyRole(roles: readonly Role[]): boolean {
    const role = this.role();
    return role !== null && roles.includes(role);
  }

  /** Each role has its own "home" page: librarians land on the dashboard, members on the catalog. */
  homeUrl(): string {
    if (!this.isLoggedIn()) return '/login';
    return this.isLibrarian() ? '/dashboard' : '/catalog';
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/login', { email, password })
      // tap() runs a side effect (saving the session) without changing the value flowing to subscribers.
      .pipe(tap((res) => this.storeSession(res)));
  }

  /** Public registration always creates a MEMBER (the server ignores any role sent). */
  register(name: string, email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/register', { name, email, password })
      .pipe(tap((res) => this.storeSession(res)));
  }

  /** Forgets the session and goes to the login page. JWTs are stateless, so there is no server call to make. */
  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this._currentUser.set(null);
    this.router.navigate(['/login']);
  }

  private storeSession(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USER_KEY, JSON.stringify(res.user));
    this._currentUser.set(res.user);
  }

  /** Restores the user after a page refresh. Corrupt or partial data is treated as "logged out". */
  private readStoredUser(): User | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw || !localStorage.getItem(TOKEN_KEY)) {
      return null;
    }
    try {
      return JSON.parse(raw) as User;
    } catch {
      return null;
    }
  }
}
