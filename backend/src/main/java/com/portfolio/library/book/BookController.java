package com.portfolio.library.book;

import com.portfolio.library.book.dto.BookRequest;
import com.portfolio.library.book.dto.BookResponse;
import com.portfolio.library.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Catalog endpoints. Any logged-in user can read; only librarians can write.
 *
 * <p>The write methods carry {@code @PreAuthorize("hasRole('LIBRARIAN')")}. Spring Security checks it
 * before the method runs and throws {@code AccessDeniedException} for members, which
 * {@code GlobalExceptionHandler} turns into a 403 JSON error. Putting the rule on the method (rather than in
 * a URL pattern list) keeps it right next to the code it protects, so it is hard to forget.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    /**
     * {@code GET /api/books?q=tolkien&genre=Fantasy&availableOnly=true&page=0&size=12&sort=title,asc}
     *
     * <p>{@code @RequestParam(required = false)} makes each filter optional; {@code defaultValue} supplies a
     * value when the parameter is missing.
     */
    @GetMapping
    public PageResponse<BookResponse> search(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "false") boolean availableOnly,
            @PageableDefault(size = 12, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.search(query, genre, availableOnly, pageable);
    }

    /** {@code GET /api/books/genres} - e.g. {@code ["Classics", "Fantasy", ...]}. Declared before "/{id}" for readability. */
    @GetMapping("/genres")
    public List<String> genres() {
        return service.genres();
    }

    /** {@code GET /api/books/{id}}. {@code @PathVariable} binds the "{id}" part of the URL. */
    @GetMapping("/{id}")
    public BookResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    /** {@code POST /api/books} - librarian only. 201 Created with the new book. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LIBRARIAN')")
    public BookResponse create(@Valid @RequestBody BookRequest request) {
        return service.create(request);
    }

    /** {@code PUT /api/books/{id}} - librarian only. Replaces all editable fields. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LIBRARIAN')")
    public BookResponse update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return service.update(id, request);
    }

    /** {@code DELETE /api/books/{id}} - librarian only. 204 No Content, or 409 if copies are on loan. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('LIBRARIAN')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
