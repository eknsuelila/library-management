package com.portfolio.library.book;

import org.springframework.data.jpa.domain.Specification;

/**
 * Re-usable, composable WHERE-clause fragments for the catalog search.
 *
 * <p>A {@link Specification} is a small function that builds one predicate with the JPA Criteria API.
 * The service chains them with {@code .and(...)}. Returning {@code null} means "no filter", so optional
 * query parameters simply drop out - no string concatenation of SQL, and therefore no SQL injection.
 */
final class BookSpecifications {

    private BookSpecifications() {
    }

    /** Case-insensitive "contains" match on title, author or ISBN. */
    static Specification<Book> matches(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) {
                return null;
            }
            String pattern = "%" + text.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("author")), pattern),
                    cb.like(root.get("isbn"), pattern));
        };
    }

    /** Exact genre match (case-insensitive so "fantasy" finds "Fantasy"). */
    static Specification<Book> hasGenre(String genre) {
        return (root, query, cb) -> genre == null || genre.isBlank()
                ? null
                : cb.equal(cb.lower(root.get("genre")), genre.trim().toLowerCase());
    }

    /** Only books with at least one copy on the shelf, when {@code onlyAvailable} is true. */
    static Specification<Book> availableOnly(boolean onlyAvailable) {
        return (root, query, cb) -> onlyAvailable ? cb.greaterThan(root.get("availableCopies"), 0) : null;
    }
}
