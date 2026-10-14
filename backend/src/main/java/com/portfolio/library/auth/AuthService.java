package com.portfolio.library.auth;

import com.portfolio.library.auth.dto.AuthResponse;
import com.portfolio.library.auth.dto.LoginRequest;
import com.portfolio.library.auth.dto.RegisterRequest;
import com.portfolio.library.auth.dto.UserResponse;
import com.portfolio.library.common.ConflictException;
import com.portfolio.library.security.JwtService;
import com.portfolio.library.user.Role;
import com.portfolio.library.user.User;
import com.portfolio.library.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login logic.
 *
 * <p>Flow: controller -> {@code AuthService} -> {@link UserRepository} (database) and {@link JwtService}
 * (token). The result is an {@link AuthResponse} that the controller returns as JSON.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Creates a new MEMBER account (never a librarian - see {@link RegisterRequest}) and returns a token
     * so the user is logged in straight away.
     *
     * <p><b>{@code @Transactional}</b> runs the method inside a database transaction: everything it writes is
     * committed together at the end, or rolled back together if an exception escapes.
     *
     * @throws ConflictException (409) if the email is already registered
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = users.save(new User(request.name().trim(), email,
                passwordEncoder.encode(request.password()), Role.MEMBER));
        return new AuthResponse(jwtService.generateToken(user), UserResponse.from(user));
    }

    /**
     * Checks the password against the stored BCrypt hash and returns a fresh token.
     *
     * <p>{@code readOnly = true} tells Hibernate nothing will be modified, which skips dirty-checking and
     * lets the database optimise. The same vague message is used for "unknown email" and "wrong password"
     * so an attacker cannot discover which emails have accounts.
     *
     * @throws BadCredentialsException (401) if the email/password pair is wrong
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return new AuthResponse(jwtService.generateToken(user), UserResponse.from(user));
    }
}
