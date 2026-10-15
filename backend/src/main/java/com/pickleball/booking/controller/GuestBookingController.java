package com.pickleball.booking.controller;

import com.pickleball.booking.dto.*;
import com.pickleball.booking.service.BookingService;
import jakarta.validation.Valid;
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

}
