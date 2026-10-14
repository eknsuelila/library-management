package com.portfolio.library.stats.dto;

/**
 * Number of loans started in one calendar month.
 *
 * @param month ISO year-month such as {@code "2026-10"} (a String so its JSON format is obvious)
 */
public record MonthlyCount(String month, long count) {
}
