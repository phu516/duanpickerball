package com.pickleball.booking.controller;

import com.pickleball.booking.dto.BookingResponse;
import com.pickleball.booking.dto.BookingRequest;
import com.pickleball.booking.service.BookingService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/bookings")
public class AdminBookingController {
    private final BookingService bookings;

    public AdminBookingController(BookingService bookings) { this.bookings = bookings; }

    @GetMapping
    public List<BookingResponse> list() { return bookings.adminList(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse createForGuest(@Valid @RequestBody BookingRequest request) {
        return bookings.create(request);
    }

    @GetMapping("/{id}")
    public BookingResponse detail(@PathVariable("id") Long id) { return bookings.adminDetail(id); }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable("id") Long id,
                                  @RequestBody Reason request) {
        return bookings.adminCancel(id, request.reason());
    }

    @PostMapping("/{id}/check-in")
    public BookingResponse checkIn(@PathVariable("id") Long id) { return bookings.checkIn(id); }

    @PostMapping("/{id}/complete")
    public BookingResponse complete(@PathVariable("id") Long id) { return bookings.complete(id); }

    @PostMapping("/{id}/no-show")
    public BookingResponse noShow(@PathVariable("id") Long id) { return bookings.noShow(id); }

    public record Reason(@NotBlank String reason) {}
}
