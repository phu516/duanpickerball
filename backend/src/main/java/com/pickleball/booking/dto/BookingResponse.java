package com.pickleball.booking.dto;

import com.pickleball.booking.entity.Booking;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record BookingResponse(Long id, String bookingCode, Long courtId, String courtName,
                              String guestName, String guestPhone, String guestEmail,
                              OffsetDateTime startAt, OffsetDateTime endAt,
                              BigDecimal totalAmount, BigDecimal depositRequired,
                              String status, OffsetDateTime holdExpiresAt,
                              String currency, String guestAccessToken) {
    public static BookingResponse from(Booking booking, String token) {
        return new BookingResponse(booking.getId(), booking.getBookingCode(),
                booking.getCourt().getId(), booking.getCourt().getName(),
                booking.getGuestName(), booking.getGuestPhone(), booking.getGuestEmail(),
                booking.getStartAt().atOffset(ZoneOffset.UTC),
                booking.getEndAt().atOffset(ZoneOffset.UTC),
                booking.getTotalAmount(), booking.getDepositRequired(), booking.getStatus(),
                booking.getHoldExpiresAt().atOffset(ZoneOffset.UTC), "VND", token);
    }
}
