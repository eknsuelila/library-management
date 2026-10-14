package com.portfolio.library.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts exceptions thrown anywhere in a controller or service into a clean {@link ApiError} JSON
 * response with the right HTTP status code.
 *
 * <p><b>{@code @RestControllerAdvice}</b> registers this class as a global "advisor" for all
 * {@code @RestController}s. When a controller method throws, Spring looks for the
 * {@code @ExceptionHandler} below whose exception type matches best and uses its return value as the
 * response. This keeps try/catch blocks out of the controllers and guarantees a consistent error format.
 *
 * <p>Status codes used:
 * <ul>
 *   <li>400 - invalid input (validation, malformed JSON, wrong parameter type, {@link BadRequestException})</li>
 *   <li>401 - wrong email/password at login. (A missing/invalid TOKEN is answered with 401 earlier, by
 *       Spring Security's entry point, before any controller runs.)</li>
 *   <li>403 - authenticated, but the role is not allowed ({@code @PreAuthorize} failed)</li>
 *   <li>404 - not found / not yours</li>
 *   <li>409 - business-rule conflict, concurrent modification, or duplicate unique value</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * {@code @Valid} on a {@code @RequestBody} failed (e.g. blank title, ISBN with 11 digits).
     * Returns every invalid field with its message so the form can highlight them.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        // putIfAbsent: if one field has several violations, keep only the first message.
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fieldErrors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ApiError body = new ApiError(Instant.now(), 400, "Bad Request", "Validation failed", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /** Unparseable JSON body, or a query parameter of the wrong type (e.g. {@code ?status=LOST}). */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleBadInput(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request");
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Business-rule violations: the message was written for the end user and is shown as-is. */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Optimistic locking failure: two requests changed the same {@code Book} row at the same time
     * (see {@code @Version} in {@code Book}). The second one loses and gets a 409; the user can simply retry,
     * and the retry will see the up-to-date number of available copies.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT,
                "This record was changed by someone else at the same time. Please reload and try again.");
    }

    /**
     * A database constraint rejected the change, e.g. two librarians adding the same ISBN at the same
     * instant (the service checks first, but only the UNIQUE index can catch a true race).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "The change conflicts with existing data");
    }

    /** Wrong email or password at login. Deliberately vague so attackers can't probe which emails exist. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /**
     * The caller IS logged in but lacks the required role, e.g. a MEMBER calling {@code POST /api/books}.
     * Thrown by Spring Security's method security when a {@code @PreAuthorize} expression is false.
     * Without this handler the exception would bubble up to the servlet container and produce an
     * HTML/empty 403; here we return the same JSON shape as every other error.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
