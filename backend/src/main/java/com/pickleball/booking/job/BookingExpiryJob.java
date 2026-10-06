package com.pickleball.booking.job;

import com.pickleball.booking.service.BookingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingExpiryJob {
    private final BookingService bookings;

    public BookingExpiryJob(BookingService bookings) { this.bookings = bookings; }

    @Scheduled(fixedDelay = 60_000)
    public void expireOldHolds() { bookings.expireHolds(); }
}
