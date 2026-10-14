package com.portfolio.library.config;

import com.portfolio.library.security.JwtAuthenticationFilter;
import com.portfolio.library.security.JwtService;
import com.portfolio.library.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * All security settings in one place.
 *
 * <p><b>Two layers of authorization</b>:
 * <ol>
 *   <li><b>URL level</b> (this class): every {@code /api/**} URL except login/register requires a valid
 *       JWT. Failing this returns <b>401 Unauthorized</b> - "we don't know who you are".</li>
 *   <li><b>Method level</b> ({@code @PreAuthorize} on controller methods, enabled by
 *       {@code @EnableMethodSecurity}): e.g. only LIBRARIANs may create books. Failing this returns
 *       <b>403 Forbidden</b> - "we know who you are, and you may not do this"
 *       (see {@code GlobalExceptionHandler#handleAccessDenied}).</li>
 * </ol>
 * Ownership rules ("members only see their own loans") are a third layer, enforced in the services
 * because they depend on the data, not just on the role.
 *
 * <p><b>{@code @Configuration}</b> marks a class whose {@code @Bean} methods create objects that Spring
 * manages and injects wherever they are needed.
 *
 * <p><b>{@code @EnableMethodSecurity}</b> turns on {@code @PreAuthorize}/{@code @PostAuthorize}. Spring wraps
 * annotated beans in a proxy that evaluates the expression before calling the real method.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** URLs anyone may call without a token. Everything else needs authentication. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/register",
            "/api/auth/login",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/h2-console/**",
            "/error"           // Spring's error page must be reachable or errors turn into 401s
    };

    /**
     * The chain of servlet filters every request passes through.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   UserDetailsService userDetailsService) throws Exception {
        return http
                // CSRF attacks abuse cookies the browser sends automatically. We use a JWT in a header that
                // our JavaScript adds explicitly and store nothing in cookies, so CSRF protection isn't needed.
                .csrf(AbstractHttpConfigurer::disable)
                // Use the CorsConfigurationSource bean below.
                .cors(Customizer.withDefaults())
                // The H2 console is rendered in an <iframe>; allow frames from the same origin (dev only).
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))
                // STATELESS: never create an HTTP session. Each request must prove who it is with its own token,
                // which lets us run several backend instances behind a load balancer without sticky sessions.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                // Unauthenticated access to a protected URL: plain 401 (instead of Spring's default
                // redirect to a login page, which makes no sense for a JSON API).
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // Run our JWT filter before Spring's username/password filter so the user is already
                // authenticated when authorization decisions are made.
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * How Spring Security loads a user by "username" (our email). Used by the JWT filter.
     * Written as a lambda because {@link UserDetailsService} has a single method.
     */
    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    /**
     * BCrypt hashes passwords with a random salt and a deliberately slow work factor (2^10 rounds by
     * default). Salting means two users with the same password get different hashes; slowness makes
     * brute-forcing a stolen database expensive. {@code matches()} re-hashes the attempt and compares.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS (Cross-Origin Resource Sharing): browsers block JavaScript from reading responses from a
     * different origin unless the server opts in. We opt in only for the configured frontend origins.
     *
     * @param allowedOrigins comma-separated list from {@code app.cors.allowed-origins}
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
