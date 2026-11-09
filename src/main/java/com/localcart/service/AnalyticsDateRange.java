package com.localcart.service;

import com.localcart.exception.BadRequestException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Resolves and validates inclusive date filters used by analytics endpoints. */
record AnalyticsDateRange(
        LocalDate from,
        LocalDate to,
        LocalDateTime startInclusive,
        LocalDateTime endExclusive
) {

    static AnalyticsDateRange resolve(LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate today = LocalDate.now();
        LocalDate to = requestedTo == null ? today : requestedTo;
        LocalDate from = requestedFrom == null ? to.minusDays(29) : requestedFrom;

        if (from.isAfter(to)) {
            throw new BadRequestException("The from date must be on or before the to date");
        }
        if (to.isAfter(today)) {
            throw new BadRequestException("The to date cannot be in the future");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > 366) {
            throw new BadRequestException("Analytics date range cannot exceed 366 days");
        }

        return new AnalyticsDateRange(
                from,
                to,
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay()
        );
    }
}
