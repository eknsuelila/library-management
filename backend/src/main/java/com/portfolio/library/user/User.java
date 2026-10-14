package com.portfolio.library.user;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * A person who can log in: either a librarian or a library member (see {@link Role}).
 *
 * <p><b>Where it fits:</b> the JWT filter loads a {@code User} from the database on every authenticated
 * request and stores it in Spring Security's context. Controllers then receive it with
 * {@code @AuthenticationPrincipal User user}, and services use it to decide what the caller may do.
 *
 * <p><b>{@code @Entity}</b> tells JPA/Hibernate that instances of this class are rows of a database table.
 * <b>{@code @Table(name = "users")}</b> names that table explicitly ("user" is a reserved word in
 * PostgreSQL, so the plural form avoids quoting problems).
 *
 * <p><b>Why implement {@link UserDetails}?</b> It is the interface Spring Security uses to describe an
 * authenticated principal (username, password hash, authorities). Implementing it directly on the entity
 * means we don't need a separate adapter class; the trade-off is that the entity now knows about Spring
 * Security, which is acceptable in an application of this size.
 */
@Entity
@Table(name = "users")
public class User implements UserDetails {

    /** {@code @Id} marks the primary key; {@code IDENTITY} lets the database generate it (auto-increment). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code @Column} maps the field to a column and documents its constraints (Hibernate validates them at startup). */
    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt hash such as {@code $2a$10$...}. The plain-text password is never stored anywhere. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * {@code @Enumerated(EnumType.STRING)} stores the enum NAME ("MEMBER") instead of its position (0/1).
     * Positions would silently change meaning if someone reordered the enum constants.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.MEMBER;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** JPA needs a no-argument constructor to create instances when reading rows. {@code protected} hides it from our code. */
    protected User() {
    }

    /**
     * Creates a new (not yet saved) user.
     *
     * @param passwordHash an already-hashed password (use {@code PasswordEncoder.encode})
     */
    public User(String name, String email, String passwordHash, Role role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }

    /** Convenience check used by services to branch on "staff vs. patron" behaviour. */
    public boolean isLibrarian() {
        return role == Role.LIBRARIAN;
    }

    // ----- UserDetails (what Spring Security needs to know about the logged-in user) -----

    /**
     * The permissions of this user. We grant exactly one authority, {@code ROLE_LIBRARIAN} or
     * {@code ROLE_MEMBER}, which {@code @PreAuthorize("hasRole('LIBRARIAN')")} checks against.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    /** Spring Security calls it "password" but it is the BCrypt hash. */
    @Override
    public String getPassword() { return passwordHash; }

    /** The email address is the login name (and the "sub" claim of the JWT). */
    @Override
    public String getUsername() { return email; }

    // isAccountNonExpired(), isAccountNonLocked(), isCredentialsNonExpired() and isEnabled() keep their
    // default implementations (all return true): this app has no account locking or expiry.
}
