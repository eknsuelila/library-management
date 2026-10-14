package com.portfolio.library.member;

import com.portfolio.library.common.PageResponse;
import com.portfolio.library.loan.LoanRepository;
import com.portfolio.library.member.dto.MemberLoanCount;
import com.portfolio.library.member.dto.MemberResponse;
import com.portfolio.library.user.Role;
import com.portfolio.library.user.User;
import com.portfolio.library.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the librarian's member list with loan counters.
 *
 * <p><b>Avoiding N+1 queries:</b> a naive version would run three COUNT queries per member (60 queries for a
 * page of 20). Instead we load one page of members (1 query), then run three GROUP BY queries restricted to
 * those member ids (3 queries), and stitch the results together in memory. 4 queries no matter the page size.
 */
@Service
@Transactional(readOnly = true)
public class MemberService {

    private final UserRepository users;
    private final LoanRepository loans;
    private final Clock clock;

    public MemberService(UserRepository users, LoanRepository loans, Clock clock) {
        this.users = users;
        this.loans = loans;
        this.clock = clock;
    }

    /**
     * One page of MEMBER accounts (librarians are excluded), optionally filtered by name/email.
     */
    public PageResponse<MemberResponse> search(String query, Pageable pageable) {
        Page<User> page = users.findAll(isMember().and(matches(query)), pageable);
        List<Long> ids = page.getContent().stream().map(User::getId).toList();
        if (ids.isEmpty()) {
            return PageResponse.from(page.map(u -> toResponse(u, Map.of(), Map.of(), Map.of())));
        }
        Map<Long, Long> active = toMap(loans.countActiveByMember(ids));
        Map<Long, Long> overdue = toMap(loans.countOverdueByMember(ids, LocalDate.now(clock)));
        Map<Long, Long> total = toMap(loans.countAllByMember(ids));
        return PageResponse.from(page.map(u -> toResponse(u, active, overdue, total)));
    }

    private static MemberResponse toResponse(User u, Map<Long, Long> active, Map<Long, Long> overdue,
                                             Map<Long, Long> total) {
        // getOrDefault: members without any loans don't appear in the GROUP BY results at all.
        return new MemberResponse(u.getId(), u.getName(), u.getEmail(), u.getCreatedAt(),
                active.getOrDefault(u.getId(), 0L),
                overdue.getOrDefault(u.getId(), 0L),
                total.getOrDefault(u.getId(), 0L));
    }

    private static Map<Long, Long> toMap(List<MemberLoanCount> counts) {
        return counts.stream().collect(Collectors.toMap(MemberLoanCount::memberId, MemberLoanCount::count));
    }

    private static Specification<User> isMember() {
        return (root, q, cb) -> cb.equal(root.get("role"), Role.MEMBER);
    }

    private static Specification<User> matches(String text) {
        return (root, q, cb) -> {
            if (text == null || text.isBlank()) {
                return null;
            }
            String pattern = "%" + text.trim().toLowerCase() + "%";
            return cb.or(cb.like(cb.lower(root.get("name")), pattern), cb.like(cb.lower(root.get("email")), pattern));
        };
    }
}
