package com.portfolio.library.member.dto;

/**
 * One row of a "count loans grouped by member" query (see {@code LoanRepository#countActiveByMember}).
 * Built directly by a JPQL constructor expression, so it needs a matching public constructor - which a record has.
 */
public record MemberLoanCount(Long memberId, long count) {
}
