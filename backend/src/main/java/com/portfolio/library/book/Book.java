package com.portfolio.library.book;

import com.portfolio.library.common.ConflictException;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * A title in the library catalog, together with how many physical copies exist and how many are on the shelf.
 *
 * <p><b>Where it fits:</b> {@code BookService} reads and writes books for the catalog endpoints, and
 * {@code LoanService} changes {@link #availableCopies} when a copy is borrowed or returned.
 *
 * <h2>Optimistic locking with {@code @Version} - why it matters here</h2>
 * Picture the last copy of "Dune" ({@code availableCopies = 1}) and two members clicking "Borrow" in the same
 * millisecond. Both requests read the row, both see 1 copy, both pass the "is a copy available?" check, and
 * both write {@code availableCopies = 0}. Two loans now exist for one physical book - a "lost update".
 *
 * <p>With {@code @Version}, Hibernate turns every UPDATE into
 * <pre>UPDATE books SET available_copies = 0, version = 8 WHERE id = 42 AND version = 7</pre>
 * The first transaction matches the row and bumps the version to 8. The second still says
 * {@code version = 7}, matches ZERO rows, and Hibernate throws an {@code OptimisticLockException}
 * (wrapped by Spring as {@code ObjectOptimisticLockingFailureException}). Its transaction rolls back - so its
 * loan row is never committed either - and the user gets a 409 "please try again". On retry they see 0 copies.
 *
 * <p>"Optimistic" because we assume conflicts are rare and don't lock anything while reading; we only detect a
 * conflict when writing. The alternative, pessimistic locking ({@code SELECT ... FOR UPDATE}), makes other
 * transactions WAIT, which costs throughput and risks deadlocks - unnecessary for a library's traffic.
 */
@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 13)
    private String isbn;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 150)
    private String author;

    @Column(nullable = false, length = 50)
    private String genre;

    @Column(name = "published_year")
    private Integer publishedYear;

    /** Copies the library owns. */
    @Column(name = "total_copies", nullable = false)
    private int totalCopies;

    /** Copies currently on the shelf. Invariant: {@code 0 <= availableCopies <= totalCopies}. */
    @Column(name = "available_copies", nullable = false)
    private int availableCopies;

    @Column(length = 2000)
    private String description;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    /**
     * {@code @Version}: Hibernate increments this on every update and adds it to the WHERE clause.
     * Never set it manually - see the class comment for the full story.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected Book() {
    }

    /** A new book starts with every copy on the shelf. */
    public Book(String isbn, String title, String author, String genre, int totalCopies) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.genre = genre;
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
    }

    /** {@code @PrePersist} is a JPA lifecycle callback: Hibernate calls it just before the first INSERT. */
    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    /** {@code @PreUpdate}: called just before each UPDATE, so updatedAt is always accurate. */
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ----- Domain behaviour: the only ways stock levels may change -----

    /**
     * Takes one copy off the shelf. The service checks availability first to produce a friendly message;
     * this guard protects the invariant even if someone calls the method from new code without checking.
     */
    public void checkOutCopy() {
        if (availableCopies <= 0) {
            throw new ConflictException("No copies of \"" + title + "\" are available right now");
        }
        availableCopies--;
    }

    /** Puts one copy back on the shelf (never more than the library owns). */
    public void returnCopy() {
        if (availableCopies < totalCopies) {
            availableCopies++;
        }
    }

    /** Number of copies that are out on loan right now. */
    public int copiesOnLoan() {
        return totalCopies - availableCopies;
    }

    /**
     * Changes how many copies the library owns while keeping the copies that are out on loan.
     * Example: 5 owned, 2 on loan (3 available). New total 4 => 2 on loan, 2 available.
     *
     * @param newTotal      the new number of owned copies
     * @param copiesOnLoan  copies currently borrowed (counted from the loans table by the service)
     * @throws ConflictException if {@code newTotal} is smaller than the copies currently on loan:
     *                           we can't "un-own" a book that a member is holding
     */
    public void changeTotalCopies(int newTotal, long copiesOnLoan) {
        if (newTotal < copiesOnLoan) {
            throw new ConflictException("Total copies cannot be less than the " + copiesOnLoan
                    + " " + (copiesOnLoan == 1 ? "copy" : "copies") + " currently on loan");
        }
        this.totalCopies = newTotal;
        this.availableCopies = (int) (newTotal - copiesOnLoan);
    }

    // ----- Getters / setters (no setters for id, version, stock: those change only through the methods above) -----

    public Long getId() { return id; }
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public Integer getPublishedYear() { return publishedYear; }
    public void setPublishedYear(Integer publishedYear) { this.publishedYear = publishedYear; }
    public int getTotalCopies() { return totalCopies; }
    public int getAvailableCopies() { return availableCopies; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
