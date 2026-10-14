package com.portfolio.library;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.library.book.Book;
import com.portfolio.library.book.BookRepository;
import com.portfolio.library.loan.Loan;
import com.portfolio.library.loan.LoanRepository;
import com.portfolio.library.security.JwtService;
import com.portfolio.library.user.Role;
import com.portfolio.library.user.User;
import com.portfolio.library.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * INTEGRATION tests: the whole application starts (security filter chain, controllers, services, JPA, Flyway
 * migrations, in-memory H2) and real HTTP requests are sent through {@link MockMvc} - an in-process HTTP client,
 * no network involved.
 *
 * <p>Where the unit tests prove each rule in isolation, these prove that everything is WIRED correctly: JWTs are
 * checked, {@code @PreAuthorize} produces 403s, validation produces 400s, rules produce 409s with readable JSON,
 * queries work against a real (H2) database, and members can't reach each other's loans.
 *
 * <p>Demo data is disabled ({@code app.seed-demo-data=false}). The Spring context - and so the database - is
 * shared by all tests in this class, so every test creates its OWN users and books (random emails/ISBNs) and
 * never depends on what other tests left behind.
 */
@SpringBootTest(properties = "app.seed-demo-data=false")
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private BookRepository books;
    @Autowired private LoanRepository loans;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    /** A fresh librarian for each test (librarians can't self-register, so we insert one directly). */
    private String librarian;

    @BeforeEach
    void createLibrarian() throws Exception {
        String email = "librarian-" + UUID.randomUUID() + "@example.com";
        users.save(new User("Test Librarian", email, passwordEncoder.encode("password123"), Role.LIBRARIAN));
        librarian = login(email, "password123");
    }

    // =========================================================================== authentication & roles

    @Test
    void protectedEndpoints_return401_withoutAValidToken() throws Exception {
        mvc.perform(get("/api/books")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/books").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registration_alwaysCreatesAMember_andTheRoleIsInTheResponseAndTheToken() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        // Trying to sneak in a role is ignored: RegisterRequest has no such field.
                        .content("""
                                {"name":"Eve","email":"eve-%s@example.com","password":"password123","role":"LIBRARIAN"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("MEMBER"))
                .andReturn().getResponse().getContentAsString();

        String token = json.readTree(body).get("token").asText();
        assertThat(jwtService.extractRole(token)).isEqualTo("MEMBER");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"wrongpass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void members_get403JsonOnLibrarianOnlyEndpoints() throws Exception {
        Member member = registerMember();
        long bookId = createBook(1);

        mvc.perform(post("/api/books").header("Authorization", bearer(member.token))
                        .contentType(MediaType.APPLICATION_JSON).content(bookJson(randomIsbn(), 1, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message", containsString("permission")));
        mvc.perform(put("/api/books/{id}", bookId).header("Authorization", bearer(member.token))
                        .contentType(MediaType.APPLICATION_JSON).content(bookJson(randomIsbn(), 1, null)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/books/{id}", bookId).header("Authorization", bearer(member.token)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/stats").header("Authorization", bearer(member.token)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/loans").header("Authorization", bearer(member.token)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/members").header("Authorization", bearer(member.token)))
                .andExpect(status().isForbidden());
        // ...but reading the catalog is fine for members.
        mvc.perform(get("/api/books/{id}", bookId).header("Authorization", bearer(member.token)))
                .andExpect(status().isOk());
    }

    // =========================================================================== catalog

    @Test
    void librarian_managesCatalog_withValidationAndUniqueIsbn() throws Exception {
        String isbn = randomIsbn();
        // Create
        mvc.perform(post("/api/books").header("Authorization", bearer(librarian))
                        .contentType(MediaType.APPLICATION_JSON).content(bookJson(isbn, 3, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.availableCopies").value(3))
                .andExpect(jsonPath("$.version").value(0));
        // Duplicate ISBN => 409
        mvc.perform(post("/api/books").header("Authorization", bearer(librarian))
                        .contentType(MediaType.APPLICATION_JSON).content(bookJson(isbn, 1, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already exists")));
        // Invalid ISBN (11 digits) and missing title => 400 with field errors
        mvc.perform(post("/api/books").header("Authorization", bearer(librarian))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isbn\":\"12345678901\",\"title\":\"\",\"author\":\"A\",\"genre\":\"G\",\"totalCopies\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.isbn").value("must be 10 or 13 digits"))
                .andExpect(jsonPath("$.fieldErrors.title").exists());
        // Search by ISBN fragment + genre list
        mvc.perform(get("/api/books").param("q", isbn).header("Authorization", bearer(librarian)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Integration Testing in Practice"));
        mvc.perform(get("/api/books/genres").header("Authorization", bearer(librarian)))
                .andExpect(jsonPath("$", hasItem("Technology")));
    }

    @Test
    void totalCopies_cannotDropBelowCopiesOnLoan() throws Exception {
        long bookId = createBook(2);
        borrow(registerMember().token, bookId).andExpect(status().isCreated());
        borrow(registerMember().token, bookId).andExpect(status().isCreated());

        mvc.perform(put("/api/books/{id}", bookId).header("Authorization", bearer(librarian))
                        .contentType(MediaType.APPLICATION_JSON).content(bookJson(randomIsbn(), 1, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("currently on loan")));
    }

    @Test
    void bookWithActiveLoans_cannotBeDeleted_untilReturned() throws Exception {
        long bookId = createBook(1);
        Member member = registerMember();
        long loanId = id(borrow(member.token, bookId));

        mvc.perform(delete("/api/books/{id}", bookId).header("Authorization", bearer(librarian)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("still on loan")));

        mvc.perform(post("/api/loans/{id}/return", loanId).header("Authorization", bearer(member.token)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/books/{id}", bookId).header("Authorization", bearer(librarian)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/books/{id}", bookId).header("Authorization", bearer(librarian)))
                .andExpect(status().isNotFound());
    }

    // =========================================================================== lending rules

    @Test
    void member_borrowsRenewsAndReturns_fullFlow() throws Exception {
        long bookId = createBook(2);
        Member member = registerMember();
        LocalDate today = LocalDate.now();

        long loanId = id(borrow(member.token, bookId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.dueDate").value(today.plusDays(14).toString())));
        mvc.perform(get("/api/books/{id}", bookId).header("Authorization", bearer(member.token)))
                .andExpect(jsonPath("$.availableCopies").value(1));

        // Same book twice => 409
        borrow(member.token, bookId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already have a copy")));

        // My loans
        mvc.perform(get("/api/loans/me").header("Authorization", bearer(member.token)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].bookId").value(bookId));

        // Renew once OK, twice 409
        mvc.perform(post("/api/loans/{id}/renew", loanId).header("Authorization", bearer(member.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value(today.plusDays(28).toString()))
                .andExpect(jsonPath("$.renewCount").value(1));
        mvc.perform(post("/api/loans/{id}/renew", loanId).header("Authorization", bearer(member.token)))
                .andExpect(status().isConflict());

        // Return (on time => no fine), copy back on the shelf, second return 409
        mvc.perform(post("/api/loans/{id}/return", loanId).header("Authorization", bearer(member.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.fine").value(0.0));
        mvc.perform(get("/api/books/{id}", bookId).header("Authorization", bearer(member.token)))
                .andExpect(jsonPath("$.availableCopies").value(2));
        mvc.perform(post("/api/loans/{id}/return", loanId).header("Authorization", bearer(member.token)))
                .andExpect(status().isConflict());
    }

    @Test
    void borrowing_isLimitedTo3Books_andToAvailableCopies() throws Exception {
        Member member = registerMember();
        for (int i = 0; i < 3; i++) {
            borrow(member.token, createBook(1)).andExpect(status().isCreated());
        }
        borrow(member.token, createBook(1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("at most 3")));

        long singleCopy = createBook(1);
        borrow(registerMember().token, singleCopy).andExpect(status().isCreated());
        borrow(registerMember().token, singleCopy)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("No copies")));
    }

    @Test
    void overdueLoan_blocksBorrowingAndRenewing_andShowsAFine() throws Exception {
        Member member = registerMember();
        long loanId = createOverdueLoan(member, 20);   // due 6 days ago

        borrow(member.token, createBook(1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("overdue")));
        mvc.perform(post("/api/loans/{id}/renew", loanId).header("Authorization", bearer(member.token)))
                .andExpect(status().isConflict());

        // The librarian's overdue filter finds it, with the fine accrued so far (6 days x $0.25).
        mvc.perform(get("/api/loans").param("status", "OVERDUE").param("memberId", String.valueOf(member.id))
                        .header("Authorization", bearer(librarian)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("OVERDUE"))
                .andExpect(jsonPath("$.content[0].fine").value(1.5));

        // Returning it records the fine.
        mvc.perform(post("/api/loans/{id}/return", loanId).header("Authorization", bearer(member.token)))
                .andExpect(jsonPath("$.fine").value(1.5));
    }

    @Test
    void members_canOnlySeeAndChangeTheirOwnLoans() throws Exception {
        Member alice = registerMember();
        Member bob = registerMember();
        long loanId = id(borrow(alice.token, createBook(1)));

        // Bob gets 404 (not 403) so he can't even tell that the loan exists.
        mvc.perform(post("/api/loans/{id}/return", loanId).header("Authorization", bearer(bob.token)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/loans/{id}/renew", loanId).header("Authorization", bearer(bob.token)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/loans/me").header("Authorization", bearer(bob.token)))
                .andExpect(jsonPath("$", hasSize(0)));
        // Bob can't borrow on Alice's behalf either.
        mvc.perform(post("/api/loans").header("Authorization", bearer(bob.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":%d,\"memberId\":%d}".formatted(createBook(1), alice.id)))
                .andExpect(status().isForbidden());
    }

    @Test
    void librarian_lendsToAMember_andSeesMembersAndStats() throws Exception {
        Member member = registerMember();
        long bookId = createBook(3);

        // A librarian must say who the loan is for.
        borrow(librarian, bookId).andExpect(status().isBadRequest());
        mvc.perform(post("/api/loans").header("Authorization", bearer(librarian))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":%d,\"memberId\":%d}".formatted(bookId, member.id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberId").value(member.id));

        mvc.perform(get("/api/members").param("q", member.email).header("Authorization", bearer(librarian)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].activeLoans").value(1))
                .andExpect(jsonPath("$.content[0].totalLoans").value(1));

        mvc.perform(get("/api/stats").header("Authorization", bearer(librarian)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTitles", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.copiesOnLoan", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.members", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.finesOutstanding").isNumber())
                .andExpect(jsonPath("$.topBorrowed", hasSize(lessThanOrEqualTo(5))))
                .andExpect(jsonPath("$.loansPerMonth", hasSize(6)));
    }

    // =========================================================================== optimistic locking

    /**
     * Simulates two transactions that both read the book while it had 1 copy, then both try to check it out.
     * Each findById outside a transaction returns a separate (detached) copy with version N. The first save
     * succeeds and bumps the row to N+1; the second save still claims version N and is rejected - the
     * "last copy borrowed twice" bug cannot happen.
     */
    @Test
    void concurrentUpdatesOfTheSameBook_areDetectedByTheVersionColumn() throws Exception {
        long bookId = createBook(1);
        Book firstReader = books.findById(bookId).orElseThrow();
        Book secondReader = books.findById(bookId).orElseThrow();

        firstReader.checkOutCopy();
        books.saveAndFlush(firstReader);

        secondReader.checkOutCopy();   // still believes 1 copy is available
        assertThatThrownBy(() -> books.saveAndFlush(secondReader))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(books.findById(bookId).orElseThrow().getAvailableCopies()).isZero();
    }

    // =========================================================================== helpers

    private record Member(long id, String email, String token) {
    }

    private Member registerMember() throws Exception {
        String email = "member-" + UUID.randomUUID() + "@example.com";
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Member\",\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(response);
        return new Member(node.get("user").get("id").asLong(), email, node.get("token").asText());
    }

    private String login(String email, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }

    /** Creates a book as the librarian and returns its id. */
    private long createBook(int copies) throws Exception {
        return id(mvc.perform(post("/api/books").header("Authorization", bearer(librarian))
                        .contentType(MediaType.APPLICATION_JSON).content(bookJson(randomIsbn(), copies, null)))
                .andExpect(status().isCreated()));
    }

    /** Inserts a loan that started {@code daysAgo} days ago directly in the database (the API only lends "today"). */
    private long createOverdueLoan(Member member, int daysAgo) throws Exception {
        long bookId = createBook(1);
        Book book = books.findById(bookId).orElseThrow();
        User user = users.findById(member.id).orElseThrow();
        book.checkOutCopy();
        books.save(book);
        return loans.save(new Loan(book, user, LocalDate.now().minusDays(daysAgo))).getId();
    }

    private ResultActions borrow(String token, long bookId) throws Exception {
        return mvc.perform(post("/api/loans").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":" + bookId + "}"));
    }

    private long id(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private static String bookJson(String isbn, int copies, Long version) {
        return """
                {"isbn":"%s","title":"Integration Testing in Practice","author":"Test Author",
                 "genre":"Technology","publishedYear":2020,"totalCopies":%d,"version":%s}
                """.formatted(isbn, copies, version);
    }

    /** A random 13-digit ISBN so tests never collide on the UNIQUE constraint. */
    private static String randomIsbn() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000_000L, 9_999_999_999_999L));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
