package com.portfolio.library.book.dto;

import com.portfolio.library.book.Book;

/**
 * JSON representation of a {@link Book}. Returned by every book endpoint.
 *
 * <p>DTOs ("Data Transfer Objects") decouple the API from the entity: we can rename a column or add an
 * internal field without breaking the frontend, and we never accidentally expose internal data.
 *
 * @param version the optimistic-locking version; the edit form sends it back in {@link BookRequest}
 */
public record BookResponse(
        Long id,
        String isbn,
        String title,
        String author,
        String genre,
        Integer publishedYear,
        int totalCopies,
        int availableCopies,
        String description,
        String coverUrl,
        Long version) {

    public static BookResponse from(Book b) {
        return new BookResponse(b.getId(), b.getIsbn(), b.getTitle(), b.getAuthor(), b.getGenre(),
                b.getPublishedYear(), b.getTotalCopies(), b.getAvailableCopies(), b.getDescription(),
                b.getCoverUrl(), b.getVersion());
    }
}
