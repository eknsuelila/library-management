package com.portfolio.library.book;

import com.portfolio.library.book.dto.BookRequest;
import com.portfolio.library.common.ConflictException;
import com.portfolio.library.loan.LoanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * UNIT tests for the catalog rules in {@link BookService} (see {@code LoanServiceTest} for how mocks work).
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock private BookRepository books;
    @Mock private LoanRepository loans;

    private BookService service;
    private Book book;

    @BeforeEach
    void setUp() {
        service = new BookService(books, loans);
        book = new Book("9780441172719", "Dune", "Frank Herbert", "Science Fiction", 5);
        ReflectionTestUtils.setField(book, "id", 1L);
        ReflectionTestUtils.setField(book, "version", 3L);
    }

    @Test
    void delete_isRejected_whileCopiesAreOnLoan() {
        when(books.findById(1L)).thenReturn(Optional.of(book));
        when(loans.countByBookIdAndReturnedDateIsNull(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("2 copies are still on loan");
        verify(books, never()).delete(any(Book.class));
    }

    @Test
    void delete_removesBookAndItsHistory_whenNothingIsOnLoan() {
        when(books.findById(1L)).thenReturn(Optional.of(book));

        service.delete(1L);

        verify(loans).deleteByBookId(1L);
        verify(books).delete(book);
    }

    @Test
    void update_isRejected_whenTotalCopiesWouldDropBelowCopiesOnLoan() {
        when(books.findById(1L)).thenReturn(Optional.of(book));
        when(loans.countByBookIdAndReturnedDateIsNull(1L)).thenReturn(3L);

        assertThatThrownBy(() -> service.update(1L, request(2, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cannot be less than the 3 copies currently on loan");
    }

    @Test
    void update_recalculatesAvailableCopiesFromCopiesOnLoan() {
        when(books.findById(1L)).thenReturn(Optional.of(book));
        when(loans.countByBookIdAndReturnedDateIsNull(1L)).thenReturn(2L);
        when(books.saveAndFlush(book)).thenReturn(book);

        var response = service.update(1L, request(4, 3L));

        assertThat(response.totalCopies()).isEqualTo(4);
        assertThat(response.availableCopies()).isEqualTo(2);      // 4 owned - 2 on loan
    }

    @Test
    void update_isRejected_whenEditingAStaleVersion() {
        when(books.findById(1L)).thenReturn(Optional.of(book));

        // The librarian loaded version 2, but the book is already at version 3.
        assertThatThrownBy(() -> service.update(1L, request(5, 2L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("changed by someone else");
        verify(loans, never()).countByBookIdAndReturnedDateIsNull(anyLong());
    }

    @Test
    void create_isRejected_forDuplicateIsbn() {
        when(books.existsByIsbn("9780441172719")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(1, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    private static BookRequest request(int totalCopies, Long version) {
        return new BookRequest("9780441172719", "Dune", "Frank Herbert", "Science Fiction", 1965, totalCopies,
                null, null, version);
    }
}
