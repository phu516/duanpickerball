package com.pickleball.booking.controller;

import com.pickleball.booking.dto.*;
import com.pickleball.booking.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/guest/bookings")
public class GuestBookingController {
    private final BookingService bookings;

    public GuestBookingController(BookingService bookings) { this.bookings = bookings; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody BookingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String requestKey) {
        return bookings.create(request, requestKey);
    }

    @GetMapping("/{code}")
    public BookingResponse detail(@PathVariable("code") String code,
                                  @RequestHeader("X-Guest-Access-Token") String token) {
        return bookings.getGuest(code, token);
    }

    @PostMapping("/{code}/cancel")
    public BookingResponse cancel(@PathVariable("code") String code,
                                  @RequestHeader("X-Guest-Access-Token") String token) {
        return bookings.cancelHold(code, token);
    }

    @PostMapping("/{code}/cancellation-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public CancellationResponse requestCancellation(@PathVariable("code") String code,
            @RequestHeader("X-Guest-Access-Token") String token,
            @Valid @RequestBody CancellationReason request) {
        return bookings.requestCancellation(code, token, request.reason());
    }

    @GetMapping("/{code}/cancellation-requests")
    public List<CancellationResponse> cancellationRequests(@PathVariable("code") String code,
            @RequestHeader("X-Guest-Access-Token") String token) {
        return bookings.guestCancellationRequests(code, token);
    }

    public record CancellationReason(@NotBlank String reason) {}
}
