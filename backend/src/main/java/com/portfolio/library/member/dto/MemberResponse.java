package com.portfolio.library.member.dto;

import java.time.Instant;

/**
 * A library member as shown on the librarian's Members page.
 *
 * @param activeLoans  books currently held (ACTIVE + OVERDUE)
 * @param overdueLoans of those, how many are past their due date
 * @param totalLoans   every loan ever, including returned ones
 */
public record MemberResponse(Long id, String name, String email, Instant joinedAt,
                             long activeLoans, long overdueLoans, long totalLoans) {
}
