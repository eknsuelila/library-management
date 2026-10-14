package com.portfolio.library.security;

import com.portfolio.library.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Creates and verifies the JSON Web Tokens (JWTs) used for stateless authentication.
 *
 * <p><b>What a JWT is:</b> three Base64URL parts separated by dots, {@code header.payload.signature}.
 * The payload of our tokens looks like:
 * <pre>
 * { "sub": "member@example.com", "uid": 2, "name": "Maya Member", "role": "MEMBER",
 *   "iat": 1760430000, "exp": 1760516400 }
 * </pre>
 * The payload is only ENCODED, not encrypted - anyone can read it (try jwt.io) - so never put secrets
 * in it. What makes it trustworthy is the signature: an HMAC-SHA hash of header+payload computed with a
 * secret key only the server knows. If anyone changes a single character (e.g. "role":"LIBRARIAN"), the
 * signature no longer matches and {@link #extractEmail} throws.
 *
 * <p><b>Where it fits:</b> {@code AuthService} calls {@link #generateToken} after a successful
 * login/registration; {@link JwtAuthenticationFilter} calls {@link #extractEmail} on every request.
 *
 * <p>{@code @Service} marks this class as a Spring-managed singleton bean that can be injected into others.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    /**
     * {@code @Value("${...}")} injects a value from application.yml (or an environment variable that
     * overrides it). The secret is Base64 so that arbitrary random bytes can be stored as text.
     */
    public JwtService(@Value("${app.jwt.secret}") String base64Secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        // hmacShaKeyFor throws at startup if the key is shorter than 256 bits - fail fast on a weak secret.
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.expirationMs = expirationMs;
    }

    /**
     * Builds a signed token for the given user.
     *
     * <p>The {@code role} and {@code name} claims are a convenience for clients (the Angular app could
     * decode them). The SERVER never trusts them for authorization: the filter reloads the user from the
     * database on each request, so a role change or a deleted account takes effect immediately.
     */
    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getEmail())                 // "sub": who the token is about
                .claim("uid", user.getId())
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                .issuedAt(now)                            // "iat"
                .expiration(new Date(now.getTime() + expirationMs))   // "exp": rejected after this instant
                .signWith(key)                            // HS256/384/512 picked from the key length (HS384 for our 48-byte dev key)
                .compact();
    }

    /**
     * Verifies the signature and expiry, then returns the email stored in the "sub" claim.
     *
     * @throws JwtException if the token is malformed, tampered with, signed with another key or expired
     */
    public String extractEmail(String token) {
        return parse(token).getSubject();
    }

    /** Reads the role claim. Used by tests to prove the role is in the token; see the class comment. */
    public String extractRole(String token) {
        return parse(token).get("role", String.class);
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
