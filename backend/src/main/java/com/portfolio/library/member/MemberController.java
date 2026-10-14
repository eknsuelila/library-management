package com.portfolio.library.member;

import com.portfolio.library.common.PageResponse;
import com.portfolio.library.member.dto.MemberResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/members} - librarian-only list of members with their loan counts.
 *
 * <p>{@code @PreAuthorize} on the CLASS applies to every method in it, so any endpoint added here later is
 * librarian-only by default ("secure by default").
 */
@RestController
@RequestMapping("/api/members")
@PreAuthorize("hasRole('LIBRARIAN')")
public class MemberController {

    private final MemberService service;

    public MemberController(MemberService service) {
        this.service = service;
    }

    /** Example: {@code GET /api/members?q=smith&page=0&size=20&sort=name,asc}. */
    @GetMapping
    public PageResponse<MemberResponse> list(
            @RequestParam(name = "q", required = false) String query,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.search(query, pageable);
    }
}
