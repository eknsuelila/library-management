import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * HTTP interceptor: a function that runs for EVERY request made with HttpClient.
 *
 * 1. Adds the "Authorization: Bearer <jwt>" header so the backend knows who is calling. Requests are immutable,
 *    so we `clone()` the request with the extra header.
 * 2. If the server answers 401 (token missing, expired or invalid), logs the user out so they land on the login
 *    page instead of a broken screen.
 *    A 403 (logged in, but wrong role) is NOT a reason to log out; the calling component shows the message.
 *
 * Registered in app.config.ts with `provideHttpClient(withInterceptors([authInterceptor]))`.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token;

  const authReq = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // A 401 from /api/auth/login just means "wrong password": don't log out for that.
      if (error.status === 401 && !req.url.includes('/api/auth/')) {
        auth.logout();
      }
      // Re-throw so the component that made the call can still show an error message.
      return throwError(() => error);
    }),
  );
};
