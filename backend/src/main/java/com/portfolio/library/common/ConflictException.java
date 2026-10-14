package com.portfolio.library.common;

/**
 * Thrown when a request is well-formed but breaks a business rule given the CURRENT state of the data,
 * e.g. "no copies available", "you already have 3 books", "this loan was already renewed".
 *
 * <p>{@link GlobalExceptionHandler} turns it into HTTP <b>409 Conflict</b> with the message shown to the user,
 * so messages should be written for end users, not developers.
 *
 * <p>It extends {@link RuntimeException} (an "unchecked" exception) so service methods don't need
 * {@code throws} clauses, and so that Spring rolls back the surrounding {@code @Transactional}
 * method automatically (Spring rolls back on unchecked exceptions by default).
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
