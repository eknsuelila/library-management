package com.portfolio.library.auth;

import com.portfolio.library.auth.dto.AuthResponse;
import com.portfolio.library.auth.dto.LoginRequest;
import com.portfolio.library.auth.dto.RegisterRequest;
import com.portfolio.library.auth.dto.UserResponse;
import com.portfolio.library.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP endpoints for registering, logging in and asking "who am I?".
 *
 * <p><b>{@code @RestController}</b> = {@code @Controller} + {@code @ResponseBody}: every method's return value
 * is written to the response as JSON (by Jackson) instead of being treated as the name of an HTML view.
 * <b>{@code @RequestMapping("/api/auth")}</b> prefixes every path in the class.
 *
 * <p>Controllers stay thin: they translate HTTP into method calls and back. All logic lives in
 * {@link AuthService}, which is easier to unit test.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /** Constructor injection: Spring passes the {@link AuthService} bean in. Fields can then be final. */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * {@code POST /api/auth/register} - creates a MEMBER account and logs them in.
     *
     * <p>{@code @Valid} triggers Bean Validation of the body; {@code @RequestBody} tells Spring to parse
     * the JSON body into a {@link RegisterRequest}. {@code @ResponseStatus(CREATED)} returns 201 instead of 200.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** {@code POST /api/auth/login} - exchanges email + password for a JWT. 401 if they don't match. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * {@code GET /api/auth/me} - the profile of the user the token belongs to.
     * {@code @AuthenticationPrincipal} injects the {@link User} the JWT filter put in the security context.
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal User user) {
        return UserResponse.from(user);
    }
}
