package com.portfolio.library.common;

import java.time.Instant;
import java.util.Map;

/**
 * The JSON body returned by EVERY failed request, for example:
 *
 * <pre>
 * {
 *   "timestamp": "2026-10-14T09:30:00Z",
 *   "status": 409,
 *   "error": "Conflict",
 *   "message": "No copies of \"Dune\" are available right now",
 *   "fieldErrors": {}
 * }
 * </pre>
 *
 * <p>Having one predictable error shape means the Angular app can show {@code message} (or the
 * per-field messages in {@code fieldErrors}) without special-casing each endpoint. It is built by
 * {@link GlobalExceptionHandler}.
 *
 * <p>A Java {@code record} is an immutable data carrier: the compiler generates the constructor,
 * accessors ({@code message()}), {@code equals}, {@code hashCode} and {@code toString}. Jackson turns
 * records into JSON objects automatically.
 *
 * @param fieldErrors validation messages keyed by field name (empty unless status is 400 validation)
 */
public record ApiError(Instant timestamp, int status, String error, String message, Map<String, String> fieldErrors) {

    /** Factory for errors that are not about individual fields. */
    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message, Map.of());
    }
}
