package com.portfolio.library.book.dto;

import jakarta.validation.constraints.*;

/**
 * Body of {@code POST /api/books} and {@code PUT /api/books/{id}} (librarian only).
 *
 * <p>Validation annotations are checked by {@code @Valid} in the controller; failures become a 400 with a
 * {@code fieldErrors} map. The Angular form mirrors these rules for instant feedback, but the server is
 * the authority because client-side checks can always be bypassed.
 *
 * @param isbn          ISBN-10 or ISBN-13 written as digits only (an ISBN-10 may end in "X", its check digit
 *                      for the value 10). Hyphens are stripped by the frontend before sending.
 * @param totalCopies   how many copies the library owns; available copies are derived from it
 * @param version       optional: the {@code version} the editing librarian loaded. If someone else saved the
 *                      book in the meantime, the update is rejected with 409 instead of silently overwriting
 *                      their change (optimistic locking applied to the edit form). Ignored on create.
 */
public record BookRequest(
        @NotBlank
        @Pattern(regexp = "^(\\d{9}[\\dX]|\\d{13})$", message = "must be 10 or 13 digits")
        String isbn,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 150) String author,
        @NotBlank @Size(max = 50) String genre,
        // 1450 ~ Gutenberg's printing press; 2100 is a sanity upper bound (annotations can't call "now").
        @Min(1450) @Max(2100) Integer publishedYear,
        @NotNull @Min(0) @Max(1000) Integer totalCopies,
        @Size(max = 2000) String description,
        @Size(max = 500) @Pattern(regexp = "^(https?://.*)?$", message = "must start with http:// or https://")
        String coverUrl,
        Long version) {
}
