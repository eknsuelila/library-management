package com.portfolio.library.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * Data access for {@link User} rows.
 *
 * <p>This is only an interface: Spring Data JPA generates the implementation at startup.
 * <ul>
 *   <li>{@link JpaRepository} provides save, findById, findAll(Pageable), delete, count ...</li>
 *   <li>{@link JpaSpecificationExecutor} adds {@code findAll(Specification, Pageable)}, used by the
 *       librarian's member search to combine optional filters.</li>
 *   <li>Methods such as {@link #findByEmailIgnoreCase} are "derived queries": Spring parses the method
 *       NAME and writes the SQL ({@code WHERE lower(email) = lower(?)}) for us.</li>
 * </ul>
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /** Used at login and by the JWT filter. Case-insensitive so "Jane@Example.com" still matches. */
    Optional<User> findByEmailIgnoreCase(String email);

    /** Used at registration to reject duplicate accounts with a friendly 409 instead of a DB error. */
    boolean existsByEmailIgnoreCase(String email);

    /** Number of members, shown on the librarian dashboard. */
    long countByRole(Role role);
}
