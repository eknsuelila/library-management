package com.portfolio.library.book;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Data access for {@link Book}. Spring Data generates the implementation (see {@code UserRepository} for how).
 */
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    boolean existsByIsbn(String isbn);

    /** Same as above but ignoring one book: used when editing, so a book doesn't "conflict" with itself. */
    boolean existsByIsbnAndIdNot(String isbn, Long id);

    /**
     * {@code @Query} lets us write JPQL by hand when a method name would be awkward. JPQL looks like SQL but
     * talks about entity classes and fields ({@code Book b}, {@code b.genre}), not tables and columns.
     */
    @Query("select distinct b.genre from Book b order by b.genre")
    List<String> findDistinctGenres();

    /** Sum of all owned copies (0 when the catalog is empty, thanks to coalesce). */
    @Query("select coalesce(sum(b.totalCopies), 0) from Book b")
    long sumTotalCopies();
}
