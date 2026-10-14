import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../core/models';

/**
 * Turns any HTTP error into a sentence that can be shown to the user.
 *
 * The backend's GlobalExceptionHandler always answers with an ApiError body, so for business-rule failures
 * (409 "No copies available", 409 "at most 3 books", 403 "no permission") we can show the server's own
 * message. Validation errors (400) list each invalid field. Anything else falls back to generic text.
 */
export function errorMessage(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      // Status 0 = the request never got an HTTP response (server down, network error, CORS failure).
      return 'Cannot reach the server. Is the backend running?';
    }
    const body = err.error as Partial<ApiError> | null;
    if (body?.fieldErrors && Object.keys(body.fieldErrors).length > 0) {
      return Object.entries(body.fieldErrors)
        .map(([field, msg]) => `${field} ${msg}`)
        .join('; ');
    }
    if (body?.message) {
      return body.message;
    }
  }
  return fallback;
}
