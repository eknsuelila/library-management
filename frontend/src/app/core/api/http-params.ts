import { HttpParams } from '@angular/common/http';

/**
 * Builds query parameters from a plain object, skipping empty values:
 *   toParams({ q: 'dune', genre: '', page: 0 })  ->  "?q=dune&page=0"
 * HttpParams takes care of URL-encoding (spaces, &, etc.).
 */
export function toParams(query: object): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}
