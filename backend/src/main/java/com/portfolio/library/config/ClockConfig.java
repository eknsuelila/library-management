package com.portfolio.library.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides the {@link Clock} that every date calculation in the app uses.
 *
 * <p><b>Why inject a Clock instead of calling {@code LocalDate.now()}?</b> Loans are full of date logic:
 * due dates, "is it overdue?", fines per day late. If the code read the real system time, a test would
 * give different results depending on the day it runs. Services call {@code LocalDate.now(clock)}
 * instead; production gets the real system clock from this bean, while unit tests pass
 * {@code Clock.fixed(...)} to freeze "today" at a known date.
 */
@Configuration
public class ClockConfig {

    /** {@code @Bean}: the return value becomes a Spring-managed object injectable anywhere as {@code Clock}. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
