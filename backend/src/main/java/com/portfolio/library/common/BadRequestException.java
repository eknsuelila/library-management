package com.portfolio.library.common;

/**
 * Thrown when the request itself is invalid in a way Bean Validation annotations can't express,
 * e.g. a librarian creating a loan without saying which member it is for.
 *
 * <p>Mapped to HTTP <b>400 Bad Request</b> by {@link GlobalExceptionHandler}.
 * Compare with {@link ConflictException} (409): 400 means "this request could never succeed as sent",
 * 409 means "this request is fine, but the current state of the data doesn't allow it right now".
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
