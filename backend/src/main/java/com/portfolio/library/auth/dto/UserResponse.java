package com.portfolio.library.auth.dto;

import com.portfolio.library.user.Role;
import com.portfolio.library.user.User;

/**
 * Public view of a user. Entities are never serialized directly: the {@link User} entity contains the
 * password hash, and exposing entities would also couple the API's JSON to the database schema.
 */
public record UserResponse(Long id, String name, String email, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
