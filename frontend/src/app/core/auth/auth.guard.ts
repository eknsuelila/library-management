import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../models';
import { AuthService } from './auth.service';

/**
 * Route guards decide whether a navigation may happen. Returning `true` allows it; returning a UrlTree
 * (`router.createUrlTree([...])` or `router.parseUrl(...)`) cancels it and redirects there instead.
 *
 * IMPORTANT: guards are a UX feature, not security. Anyone can edit JavaScript in their browser. The real
 * protection is on the server, which checks the JWT and the role on every API call (401/403). Guards just
 * keep users away from pages whose API calls would fail anyway.
 */

/** Only logged-in users may enter; others are sent to /login. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? true : inject(Router).createUrlTree(['/login']);
};

/** Login/register pages: users who are already logged in are sent to their home page. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? inject(Router).parseUrl(auth.homeUrl()) : true;
};

/**
 * Role-based guard. The allowed roles are declared on the route itself:
 *
 *   { path: 'dashboard', canActivate: [roleGuard], data: { roles: ['LIBRARIAN'] }, ... }
 *
 * - not logged in        -> /login
 * - logged in, wrong role -> that user's own home page (members -> /catalog)
 * - no roles configured   -> any logged-in user may enter
 */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login']);
  }
  // route.data is an index signature, hence the ['roles'] bracket syntax (tsconfig: noPropertyAccessFromIndexSignature).
  const roles = route.data['roles'] as Role[] | undefined;
  if (!roles || auth.hasAnyRole(roles)) {
    return true;
  }
  return router.parseUrl(auth.homeUrl());
};

/**
 * Used on the empty path "" to send each user to the right starting page.
 * (Angular 17's `redirectTo` must be a fixed string; a guard lets us pick the target dynamically.)
 */
export const homeRedirectGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return inject(Router).parseUrl(auth.homeUrl());
};
