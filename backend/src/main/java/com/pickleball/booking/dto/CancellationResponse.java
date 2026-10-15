package com.pickleball.booking.dto;

import com.pickleball.booking.entity.BookingCancellationRequest;
import com.pickleball.booking.entity.Booking;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record CancellationResponse(Long id, Long bookingId, String bookingCode,
                                   String guestName, String guestPhone, String courtName,
                                   OffsetDateTime startAt, OffsetDateTime endAt,
                                   BigDecimal totalAmount, BigDecimal depositRequired,
                                   String bookingStatus, String recordType, String reason,
                                   String status, OffsetDateTime createdAt,
                                   OffsetDateTime reviewedAt, String reviewReason) {
    public static CancellationResponse from(BookingCancellationRequest request) {
        var booking = request.getBooking();
        return new CancellationResponse(request.getId(), booking.getId(), booking.getBookingCode(),
                booking.getGuestName(), booking.getGuestPhone(), booking.getCourt().getName(),
                booking.getStartAt().atOffset(ZoneOffset.UTC), booking.getEndAt().atOffset(ZoneOffset.UTC),
                booking.getTotalAmount(), booking.getDepositRequired(), booking.getStatus(),
                "REQUEST", request.getReason(), request.getStatus(),
                request.getCreatedAt().atOffset(ZoneOffset.UTC),
                request.getReviewedAt() == null ? null : request.getReviewedAt().atOffset(ZoneOffset.UTC),
                request.getReviewReason());
    }

    public static CancellationResponse fromAdminCancellation(Booking booking) {
        var cancelledAt = (booking.getCancelledAt() == null ? booking.getCreatedAt() : booking.getCancelledAt())
                .atOffset(ZoneOffset.UTC);
        return new CancellationResponse(booking.getId(), booking.getId(), booking.getBookingCode(),
                booking.getGuestName(), booking.getGuestPhone(), booking.getCourt().getName(),
                booking.getStartAt().atOffset(ZoneOffset.UTC), booking.getEndAt().atOffset(ZoneOffset.UTC),
                booking.getTotalAmount(), booking.getDepositRequired(), booking.getStatus(),
                "ADMIN_CANCELLATION", booking.getCancelReason(), "CANCELLED", cancelledAt,
                cancelledAt, null);
    }
}
