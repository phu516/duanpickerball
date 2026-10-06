package com.pickleball.booking.dto;

import com.pickleball.booking.entity.BookingCancellationRequest;
import java.time.LocalDateTime;

public record CancellationResponse(Long id, Long bookingId, String reason, String status,
                                   LocalDateTime createdAt) {
    public static CancellationResponse from(BookingCancellationRequest request) {
        return new CancellationResponse(request.getId(), request.getBooking().getId(),
                request.getReason(), request.getStatus(), request.getCreatedAt());
    }
}
