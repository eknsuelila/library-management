package com.portfolio.library.auth.dto;

/**
 * Body returned by {@code POST /api/auth/login} and {@code POST /api/auth/register}:
 * the JWT to send on later requests, plus the user's profile (including the role) so the
 * Angular app can render a role-aware navbar without decoding the token.
 */
public record AuthResponse(String token, UserResponse user) {
}
