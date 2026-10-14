package com.portfolio.library.config;

import com.portfolio.library.book.Book;
import com.portfolio.library.book.BookRepository;
import com.portfolio.library.loan.Loan;
import com.portfolio.library.loan.LoanRepository;
import com.portfolio.library.user.Role;
import com.portfolio.library.user.User;
import com.portfolio.library.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills an EMPTY database with a realistic demo library so that anyone trying the app (a recruiter, a
 * reviewer) can log in and click around immediately:
 * <ul>
 *   <li>1 librarian: {@value #LIBRARIAN_EMAIL} / {@value #DEMO_PASSWORD}</li>
 *   <li>5 members, including {@value #MEMBER_EMAIL} / {@value #DEMO_PASSWORD}</li>
 *   <li>26 well-known books across 12 genres</li>
 *   <li>Loan history over the last 6 months (so the dashboard charts have data), a few current loans,
 *       two overdue loans and some recorded fines.</li>
 * </ul>
 *
 * <p><b>{@code CommandLineRunner}</b>: Spring Boot calls {@link #run} once, after the application context has
 * started (and therefore after Flyway created the tables).
 *
 * <p><b>{@code @ConditionalOnProperty}</b>: this bean only exists when {@code app.seed-demo-data=true}.
 * Integration tests set it to false so that they start from an empty database.
 *
 * <p>All dates are relative to "today" from the injected {@link Clock}, so the demo always looks current:
 * the overdue loans are always overdue, and the chart always shows the last 6 months.
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    public static final String LIBRARIAN_EMAIL = "librarian@example.com";
    public static final String MEMBER_EMAIL = "member@example.com";
    public static final String DEMO_PASSWORD = "password123";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final BookRepository books;
    private final LoanRepository loans;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoDataSeeder(UserRepository users, BookRepository books, LoanRepository loans,
                          PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.books = books;
        this.loans = loans;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Seeds the data in a single transaction. Skips everything if the librarian account already exists, so
     * restarting against a persistent PostgreSQL database never creates duplicates.
     */
    @Override
    @Transactional
    public void run(String... args) {
        if (users.existsByEmailIgnoreCase(LIBRARIAN_EMAIL)) {
            return;
        }
        // Hash once and reuse: BCrypt is deliberately slow (~100 ms), no need to pay that 6 times.
        String hash = passwordEncoder.encode(DEMO_PASSWORD);

        users.save(new User("Lena Librarian", LIBRARIAN_EMAIL, hash, Role.LIBRARIAN));
        User maya = users.save(new User("Maya Member", MEMBER_EMAIL, hash, Role.MEMBER));
        User alex = users.save(new User("Alex Johnson", "alex@example.com", hash, Role.MEMBER));
        User priya = users.save(new User("Priya Patel", "priya@example.com", hash, Role.MEMBER));
        User sam = users.save(new User("Sam Lee", "sam@example.com", hash, Role.MEMBER));
        User olivia = users.save(new User("Olivia Brown", "olivia@example.com", hash, Role.MEMBER));
        List<User> members = List.of(maya, alex, priya, sam, olivia);

        List<Book> catalog = seedBooks();
        LocalDate today = LocalDate.now(clock);

        // ---- Past, returned loans spread over the last ~6 months (feeds the charts and "top borrowed") ----
        // Each row: {member index, book index, borrowed N days ago, kept for D days}. D > 14 => returned late => fine.
        int[][] history = {
                {0, 17, 170, 10}, {1, 0, 165, 12}, {2, 9, 160, 14}, {3, 17, 150, 9}, {4, 1, 145, 20},
                {0, 1, 130, 7}, {1, 9, 125, 14}, {2, 17, 118, 18}, {3, 3, 110, 5}, {4, 9, 100, 13},
                {0, 4, 95, 14}, {1, 17, 88, 11}, {2, 1, 80, 16}, {3, 9, 74, 8}, {4, 20, 66, 14},
                {0, 17, 60, 20}, {1, 1, 52, 10}, {2, 19, 47, 12}, {3, 0, 41, 14}, {4, 17, 35, 9},
                {0, 9, 30, 6}, {1, 20, 26, 15}, {2, 3, 22, 10}, {3, 1, 18, 7}, {4, 24, 15, 4},
        };
        for (int[] h : history) {
            Loan loan = new Loan(catalog.get(h[1]), members.get(h[0]), today.minusDays(h[2]));
            loan.markReturned(today.minusDays(h[2] - h[3]));   // records a fine if kept > 14 days
            loans.save(loan);
        }

        // ---- Current loans (these take copies off the shelf) ----
        lend(catalog.get(9), maya, today.minusDays(5));       // The Hobbit: due in 9 days
        lend(catalog.get(13), maya, today.minusDays(12));     // Murder on the Orient Express: due in 2 days
        lend(catalog.get(2), alex, today.minusDays(20));      // Pride and Prejudice: OVERDUE by 6 days
        lend(catalog.get(18), alex, today.minusDays(3));
        lend(catalog.get(17), priya, today.minusDays(4));     // Dune
        lend(catalog.get(24), priya, today.minusDays(1));     // The Pragmatic Programmer: its only copy
        lend(catalog.get(7), olivia, today.minusDays(30));    // Dracula: OVERDUE by 16 days
        lend(catalog.get(1), sam, today.minusDays(8));

        log.info("Seeded demo library: {} books, {} members. Logins: {} / {} and {} / {}",
                catalog.size(), members.size(), LIBRARIAN_EMAIL, DEMO_PASSWORD, MEMBER_EMAIL, DEMO_PASSWORD);
    }

    /** Creates an open loan and takes the copy off the shelf, exactly like a real borrow would. */
    private void lend(Book book, User member, LocalDate borrowedDate) {
        book.checkOutCopy();
        loans.save(new Loan(book, member, borrowedDate));
    }

    private List<Book> seedBooks() {
        List<Book> list = new ArrayList<>();
        // index 0-4
        list.add(book("9780061120084", "To Kill a Mockingbird", "Harper Lee", "Classics", 1960, 4,
                "A young girl's view of racial injustice in a small Alabama town."));
        list.add(book("9780451524935", "1984", "George Orwell", "Dystopian", 1949, 5,
                "A chilling portrait of a surveillance state where Big Brother is always watching."));
        list.add(book("9780141439518", "Pride and Prejudice", "Jane Austen", "Romance", 1813, 3,
                "Elizabeth Bennet and Mr. Darcy navigate pride, prejudice and the marriage market."));
        list.add(book("9780743273565", "The Great Gatsby", "F. Scott Fitzgerald", "Classics", 1925, 3,
                "The mysterious Jay Gatsby and his obsession with Daisy Buchanan in the Jazz Age."));
        list.add(book("9780142437247", "Moby-Dick", "Herman Melville", "Adventure", 1851, 2,
                "Captain Ahab's relentless hunt for the white whale."));
        // 5-9
        list.add(book("9780141441146", "Jane Eyre", "Charlotte Brontë", "Romance", 1847, 2,
                "An orphaned governess finds independence and love at Thornfield Hall."));
        list.add(book("9780141439471", "Frankenstein", "Mary Shelley", "Horror", 1818, 2,
                "A scientist creates life and must face the consequences."));
        list.add(book("9780141439846", "Dracula", "Bram Stoker", "Horror", 1897, 2,
                "The classic vampire tale told through letters and diaries."));
        list.add(book("9780060850524", "Brave New World", "Aldous Huxley", "Dystopian", 1932, 3,
                "A future society engineered for happiness, at the cost of freedom."));
        list.add(book("9780547928227", "The Hobbit", "J.R.R. Tolkien", "Fantasy", 1937, 4,
                "Bilbo Baggins is swept into a quest to reclaim a dragon's treasure."));
        // 10-14
        list.add(book("9780547928210", "The Fellowship of the Ring", "J.R.R. Tolkien", "Fantasy", 1954, 3,
                "Frodo sets out to destroy the One Ring before Sauron can reclaim it."));
        list.add(book("9781451673319", "Fahrenheit 451", "Ray Bradbury", "Dystopian", 1953, 3,
                "A fireman whose job is to burn books begins to question everything."));
        list.add(book("9780316769488", "The Catcher in the Rye", "J.D. Salinger", "Classics", 1951, 2,
                "Holden Caulfield's restless days in New York City."));
        list.add(book("9780062693662", "Murder on the Orient Express", "Agatha Christie", "Mystery", 1934, 3,
                "Hercule Poirot investigates a murder aboard a snowbound train."));
        list.add(book("9780141034324", "The Hound of the Baskervilles", "Arthur Conan Doyle", "Mystery", 1902, 2,
                "Sherlock Holmes and a legendary hound on the moors of Devon."));
        // 15-19
        list.add(book("9780143058144", "Crime and Punishment", "Fyodor Dostoevsky", "Classics", 1866, 2,
                "A former student commits a murder and wrestles with his conscience."));
        list.add(book("9780147514011", "Little Women", "Louisa May Alcott", "Classics", 1868, 2,
                "The four March sisters grow up during and after the American Civil War."));
        list.add(book("9780441172719", "Dune", "Frank Herbert", "Science Fiction", 1965, 4,
                "Paul Atreides on the desert planet Arrakis, source of the universe's most valuable spice."));
        list.add(book("9780553293357", "Foundation", "Isaac Asimov", "Science Fiction", 1951, 3,
                "A mathematician predicts the fall of the Galactic Empire and plans for what comes after."));
        list.add(book("9780345391803", "The Hitchhiker's Guide to the Galaxy", "Douglas Adams", "Science Fiction",
                1979, 3, "Arthur Dent escapes Earth's demolition with a towel and a guidebook."));
        // 20-25
        list.add(book("9780553380163", "A Brief History of Time", "Stephen Hawking", "Science", 1988, 2,
                "From the Big Bang to black holes, cosmology explained for everyone."));
        list.add(book("9780062316097", "Sapiens", "Yuval Noah Harari", "History", 2011, 3,
                "A brief history of humankind, from foragers to the modern age."));
        list.add(book("9780553296983", "The Diary of a Young Girl", "Anne Frank", "Biography", 1947, 2,
                "The diary Anne Frank kept while hiding from the Nazis in Amsterdam."));
        list.add(book("9780132350884", "Clean Code", "Robert C. Martin", "Technology", 2008, 2,
                "A handbook of agile software craftsmanship."));
        list.add(book("9780201616224", "The Pragmatic Programmer", "Andrew Hunt & David Thomas", "Technology",
                1999, 1, "Practical advice for becoming a better, more effective developer."));
        list.add(book("9780451526342", "Animal Farm", "George Orwell", "Dystopian", 1945, 3,
                "Farm animals overthrow their owner, only to find new masters."));
        return books.saveAll(list);
    }

    private static Book book(String isbn, String title, String author, String genre, int year, int copies,
                             String description) {
        Book b = new Book(isbn, title, author, genre, copies);
        b.setPublishedYear(year);
        b.setDescription(description);
        // Open Library serves covers by ISBN. "?default=false" makes it answer 404 (instead of a blank image)
        // when it has no cover, so the frontend's (error) handler can show a placeholder.
        b.setCoverUrl("https://covers.openlibrary.org/b/isbn/" + isbn + "-M.jpg?default=false");
        return b;
    }
}
