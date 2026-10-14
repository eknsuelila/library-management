package com.portfolio.library.book;

import com.portfolio.library.book.dto.BookRequest;
import com.portfolio.library.book.dto.BookResponse;
import com.portfolio.library.common.ConflictException;
import com.portfolio.library.common.PageResponse;
import com.portfolio.library.common.ResourceNotFoundException;
import com.portfolio.library.loan.LoanRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.portfolio.library.book.BookSpecifications.*;

/**
 * Catalog logic: searching, creating, editing and deleting books.
 *
 * <p>Reading is open to every logged-in user; writing is restricted to librarians by {@code @PreAuthorize}
 * in {@link BookController}. This service enforces the DATA rules:
 * <ul>
 *   <li>ISBNs are unique (409 on duplicates);</li>
 *   <li>total copies can't drop below the copies currently on loan (409);</li>
 *   <li>a book with active loans can't be deleted (409);</li>
 *   <li>an edit based on an outdated version is rejected (409, optimistic locking).</li>
 * </ul>
 */
@Service
@Transactional
public class BookService {

    private final BookRepository books;
    private final LoanRepository loans;

    public BookService(BookRepository books, LoanRepository loans) {
        this.books = books;
        this.loans = loans;
    }

    /**
     * Catalog search. Every filter is optional; {@code null} Specifications are ignored by {@code and()}.
     *
     * @param query         text matched against title, author and ISBN
     * @param genre         exact genre
     * @param availableOnly when true, hide books with no copies on the shelf
     */
    @Transactional(readOnly = true)
    public PageResponse<BookResponse> search(String query, String genre, boolean availableOnly, Pageable pageable) {
        var spec = matches(query).and(hasGenre(genre)).and(availableOnly(availableOnly));
        return PageResponse.from(books.findAll(spec, pageable).map(BookResponse::from));
    }

    /** Distinct genres, alphabetically, for the catalog's genre filter dropdown. */
    @Transactional(readOnly = true)
    public List<String> genres() {
        return books.findDistinctGenres();
    }

    @Transactional(readOnly = true)
    public BookResponse get(Long id) {
        return BookResponse.from(find(id));
    }

    /** Adds a title to the catalog with all copies available. 409 if the ISBN already exists. */
    public BookResponse create(BookRequest request) {
        String isbn = request.isbn().trim();
        if (books.existsByIsbn(isbn)) {
            throw new ConflictException("A book with ISBN " + isbn + " already exists");
        }
        Book book = new Book(isbn, request.title().trim(), request.author().trim(), request.genre().trim(),
                request.totalCopies());
        applyDetails(book, request);
        return BookResponse.from(books.save(book));
    }

    /**
     * Updates a book. Changing {@code totalCopies} recalculates {@code availableCopies} from the number of
     * copies actually on loan (counted in the loans table, the source of truth), see
     * {@link Book#changeTotalCopies}.
     */
    public BookResponse update(Long id, BookRequest request) {
        Book book = find(id);
        // Optimistic locking for the edit form: the client sends the version it loaded. If the row has moved
        // on (another librarian saved, or a member borrowed a copy), refuse instead of overwriting.
        if (request.version() != null && !request.version().equals(book.getVersion())) {
            throw new ConflictException("This book was changed by someone else while you were editing. "
                    + "Reload the page to see the latest version.");
        }
        String isbn = request.isbn().trim();
        if (books.existsByIsbnAndIdNot(isbn, id)) {
            throw new ConflictException("A book with ISBN " + isbn + " already exists");
        }
        book.setIsbn(isbn);
        book.setTitle(request.title().trim());
        book.setAuthor(request.author().trim());
        book.setGenre(request.genre().trim());
        book.changeTotalCopies(request.totalCopies(), loans.countByBookIdAndReturnedDateIsNull(id));
        applyDetails(book, request);
        // saveAndFlush runs the UPDATE now, so a concurrent change is detected (via @Version) inside this call.
        return BookResponse.from(books.saveAndFlush(book));
    }

    /**
     * Removes a book from the catalog. Refused while any copy is on loan - the library would lose track of
     * books that members are still holding. The book's past (returned) loans are deleted with it.
     *
     * <p>Trade-off: deleting history also removes those loans from the statistics. A production system would
     * "soft delete" (an {@code archived} flag) instead; see docs/HOW-IT-WORKS.md, "Possible improvements".
     */
    public void delete(Long id) {
        Book book = find(id);
        long onLoan = loans.countByBookIdAndReturnedDateIsNull(id);
        if (onLoan > 0) {
            throw new ConflictException("Cannot delete \"" + book.getTitle() + "\": " + onLoan
                    + (onLoan == 1 ? " copy is" : " copies are") + " still on loan");
        }
        loans.deleteByBookId(id);
        books.delete(book);
    }

    private Book find(Long id) {
        return books.findById(id).orElseThrow(() -> new ResourceNotFoundException("Book " + id + " not found"));
    }

    /** Copies the optional fields, turning blank strings into nulls so the database doesn't store "". */
    private static void applyDetails(Book book, BookRequest r) {
        book.setPublishedYear(r.publishedYear());
        book.setDescription(blankToNull(r.description()));
        book.setCoverUrl(blankToNull(r.coverUrl()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
