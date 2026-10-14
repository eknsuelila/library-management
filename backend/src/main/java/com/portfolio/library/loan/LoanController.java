package com.portfolio.library.loan;

import com.portfolio.library.common.PageResponse;
import com.portfolio.library.loan.dto.BorrowRequest;
import com.portfolio.library.loan.dto.LoanResponse;
import com.portfolio.library.user.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * HTTP endpoints for loans. All require a valid JWT (see {@code SecurityConfig}); the role and ownership
 * rules are described per method. Business logic lives in {@link LoanService}.
 */
@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService service;

    public LoanController(LoanService service) {
        this.service = service;
    }

    /**
     * {@code POST /api/loans} - borrow a book.
     * Member body: {@code {"bookId": 7}}. Librarian body: {@code {"bookId": 7, "memberId": 3}}.
     * 201 with the new loan, or 409 with the rule that was broken.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoanResponse borrow(@AuthenticationPrincipal User user, @Valid @RequestBody BorrowRequest request) {
        return service.borrow(user, request);
    }

    /** {@code POST /api/loans/{id}/return} - return a book (members: own loans only). */
    @PostMapping("/{id}/return")
    public LoanResponse returnLoan(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return service.returnLoan(user, id);
    }

    /** {@code POST /api/loans/{id}/renew} - extend the due date by 14 days, once (members: own loans only). */
    @PostMapping("/{id}/renew")
    public LoanResponse renew(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return service.renew(user, id);
    }

    /** {@code GET /api/loans/me} - the caller's own loans, newest first. */
    @GetMapping("/me")
    public List<LoanResponse> myLoans(@AuthenticationPrincipal User user) {
        return service.myLoans(user);
    }

    /**
     * {@code GET /api/loans?status=OVERDUE&memberId=3&page=0&size=20&sort=dueDate,asc} - every loan (librarian only).
     *
     * <p><b>{@code @PreAuthorize}</b> is evaluated by Spring Security BEFORE the method body runs. If the
     * expression is false an {@code AccessDeniedException} is thrown and the client gets 403.
     * {@code hasRole('LIBRARIAN')} checks for the authority {@code ROLE_LIBRARIAN} (the prefix is implied).
     *
     * <p>{@code @PageableDefault} supplies page size and sort when the client doesn't send them; Spring builds
     * the {@link Pageable} from the {@code page}, {@code size} and {@code sort} query parameters.
     */
    @GetMapping
    @PreAuthorize("hasRole('LIBRARIAN')")
    public PageResponse<LoanResponse> search(
            @RequestParam(required = false) LoanStatus status,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long bookId,
            @PageableDefault(size = 20, sort = "borrowedDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(status, memberId, bookId, pageable);
    }
}
