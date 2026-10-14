package com.portfolio.library.user;

/**
 * The two kinds of account in the system.
 *
 * <ul>
 *   <li>{@link #LIBRARIAN} - staff: manages the catalog, lends books to members, sees every loan and
 *       the statistics dashboard.</li>
 *   <li>{@link #MEMBER} - a library patron: browses the catalog, borrows, renews and returns
 *       their OWN loans only.</li>
 * </ul>
 *
 * <p>Spring Security represents permissions as "authorities" (plain strings). By convention a role is an
 * authority with the prefix {@code ROLE_}, which is what {@code hasRole('LIBRARIAN')} in
 * {@code @PreAuthorize} looks for. {@link #authority()} produces that string.
 *
 * <p>The enum is stored in the {@code users.role} column as its name (see {@code @Enumerated(STRING)}
 * in {@link User}).
 */
public enum Role {
    LIBRARIAN,
    MEMBER;

    /** The Spring Security authority for this role, e.g. {@code "ROLE_LIBRARIAN"}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
