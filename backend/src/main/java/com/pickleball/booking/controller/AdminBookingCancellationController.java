package com.pickleball.booking.controller;

import com.pickleball.booking.dto.CancellationResponse;
import com.pickleball.booking.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/booking-cancellation-requests")
public class AdminBookingCancellationController {
    private final BookingService bookings;
    public AdminBookingCancellationController(BookingService bookings) { this.bookings = bookings; }
    @GetMapping public List<CancellationResponse> list() { return bookings.adminCancellationRequests(); }
    @PostMapping("/{id}/approve")
    public CancellationResponse approve(@PathVariable Long id, @Valid @RequestBody Reason request) {
        return bookings.reviewCancellation(id, true, request.reason());
    }
    @PostMapping("/{id}/reject")
    public CancellationResponse reject(@PathVariable Long id, @Valid @RequestBody Reason request) {
        return bookings.reviewCancellation(id, false, request.reason());
    }
    public record Reason(@NotBlank String reason) {}
}
