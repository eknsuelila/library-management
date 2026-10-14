package com.portfolio.library.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once for every HTTP request, BEFORE any controller, and answers "who is calling?".
 *
 * <p>Steps:
 * <ol>
 *   <li>Look for the header {@code Authorization: Bearer <token>}.</li>
 *   <li>Verify the token's signature/expiry and read the email from it ({@link JwtService}).</li>
 *   <li>Load the user (and therefore their CURRENT role) from the database.</li>
 *   <li>Put an {@code Authentication} object into the {@link SecurityContextHolder}. From now on Spring
 *       Security considers the request authenticated with that user's authorities, and controllers can
 *       receive the user via {@code @AuthenticationPrincipal}.</li>
 * </ol>
 *
 * <p>If there is no token or it is invalid, the filter does NOTHING and just passes the request on.
 * Public endpoints (login/register) then work as normal, and protected endpoints are rejected with
 * 401 by the authentication entry point configured in {@code SecurityConfig}. Keeping the filter
 * "silent" means there is exactly one place that decides about 401 responses.
 *
 * <p>It is not a {@code @Component} on purpose: Spring Boot would otherwise ALSO register it as a plain
 * servlet filter, running it twice. {@code SecurityConfig} creates it and adds it to the security chain.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String email = jwtService.extractEmail(header.substring(BEARER_PREFIX.length()));
                UserDetails user = userDetailsService.loadUserByUsername(email);
                // Three-argument constructor = "authenticated" token. Credentials are null: we never
                // need the password again after the token was verified.
                var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
                // Invalid/expired token or deleted user: continue unauthenticated (=> 401 on protected URLs).
            }
        }
        // Always hand the request to the next filter in the chain (and finally to the controller).
        chain.doFilter(request, response);
    }
}
