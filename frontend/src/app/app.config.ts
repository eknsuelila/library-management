import { ApplicationConfig } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';

/**
 * Application-wide providers (in standalone Angular this replaces the old AppModule).
 *
 * - provideRouter: enables routing. withComponentInputBinding() lets route parameters such as ":id" arrive
 *   directly as component @Input()s (see BookFormComponent).
 * - provideHttpClient: enables HttpClient, with our JWT interceptor plugged into every request.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
