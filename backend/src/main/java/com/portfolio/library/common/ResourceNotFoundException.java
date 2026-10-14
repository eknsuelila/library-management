package com.portfolio.library.common;

/**
 * Thrown when a requested record does not exist - OR exists but the caller is not allowed to know
 * about it (a member asking for another member's loan). Returning 404 in both cases avoids leaking
 * which ids exist. Mapped to HTTP <b>404 Not Found</b> by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
