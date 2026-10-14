package com.portfolio.library.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/register}.
 *
 * <p>Notice there is NO role field: public registration always creates a MEMBER. If the client could
 * send {@code "role": "LIBRARIAN"}, anyone could make themselves staff ("mass assignment" vulnerability).
 * Librarian accounts are created by seeding/administration only.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100, message = "must be at least 8 characters") String password) {
}
