package com.portfolio.library.stats.dto;

/** How many times a book has been borrowed in total; one entry of the "Top 5 most borrowed" chart. */
public record BookBorrowCount(Long bookId, String title, String author, long loans) {
}
