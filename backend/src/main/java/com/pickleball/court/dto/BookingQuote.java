package com.pickleball.court.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record BookingQuote(Long courtId, OffsetDateTime startAt, OffsetDateTime endAt,
                           BigDecimal totalAmount, BigDecimal depositRequired,
                           BigDecimal remainingAmount, String currency,
                           List<PriceSegment> segments) {
    public record PriceSegment(OffsetDateTime startAt, OffsetDateTime endAt,
                               BigDecimal hourlyPrice, BigDecimal amount) {}
}
