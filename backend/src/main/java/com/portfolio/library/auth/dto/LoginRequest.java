package com.portfolio.library.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/auth/login}.
 *
 * <p>{@code @NotBlank} is a Bean Validation constraint: when the controller parameter is annotated with
 * {@code @Valid}, Spring checks it before the method runs and answers 400 with field errors if it fails.
 */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
