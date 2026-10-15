package com.pickleball.booking;

import com.pickleball.booking.dto.BookingRequest;
import com.pickleball.booking.dto.BookingResponse;
import com.pickleball.booking.entity.Booking;
import com.pickleball.booking.repository.BookingRepository;
import com.pickleball.booking.service.BookingService;
import com.pickleball.common.exception.BusinessException;
import com.pickleball.court.entity.Court;
import com.pickleball.court.entity.CourtPriceRule;
import com.pickleball.court.repository.CourtPriceRuleRepository;
import com.pickleball.court.repository.CourtRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class BookingServiceIntegrationTest {
    @Autowired BookingService bookings;
    @Autowired BookingRepository bookingRecords;
    @Autowired CourtRepository courts;
    @Autowired CourtPriceRuleRepository prices;
    private Court court;
    private OffsetDateTime start;

    @BeforeEach
    void setUp() {
        court = courts.save(new Court("T" + UUID.randomUUID().toString().substring(0, 8),
                "Sân kiểm thử", "Acrylic", true, ""));
        for (int day = 1; day <= 7; day++) prices.save(new CourtPriceRule(court, day,
                LocalTime.of(6, 0), LocalTime.of(23, 0), BigDecimal.valueOf(120_000),
                LocalDate.now().minusDays(1), null));
        start = LocalDate.now().plusDays(2).atTime(17, 0).atOffset(ZoneOffset.ofHours(7));
    }

    @Test
    void oneGuestCanHoldSeveralIndependentCourtsAndAdjacentSlots() {
        BookingResponse first = bookings.create(request(court.getId(), start, start.plusHours(2)));
        BookingResponse adjacent = bookings.create(request(court.getId(), start.plusHours(2), start.plusHours(4)));
        assertNotEquals(first.bookingCode(), adjacent.bookingCode());
        assertEquals(BigDecimal.valueOf(240_000).setScale(2), first.totalAmount());
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> bookings.create(request(court.getId(), start.plusHours(1), start.plusHours(3))));
        assertEquals("COURT_UNAVAILABLE", conflict.code());
        BookingResponse cancelled = bookings.adminCancel(first.id(), "Kiểm thử hủy phiếu");
        assertEquals("CANCELLED", cancelled.status());
        assertEquals("HOLD", bookings.getGuest(adjacent.bookingCode(), adjacent.guestAccessToken()).status());
    }

    @Test
    void onlyOneConcurrentRequestCanHoldSameCourtAndTime() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            ready.countDown();
            go.await();
            try { bookings.create(request(court.getId(), start, start.plusHours(2))); return true; }
            catch (BusinessException exception) {
                assertEquals("COURT_UNAVAILABLE", exception.code());
                return false;
            }
        };
        try {
            Future<Boolean> a = pool.submit(attempt);
            Future<Boolean> b = pool.submit(attempt);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            assertNotEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void retryWithSameRequestKeyReturnsSameBooking() {
        BookingRequest request = request(court.getId(), start, start.plusHours(2));
        String key = UUID.randomUUID().toString();
        BookingResponse first = bookings.create(request, key);
        BookingResponse retry = bookings.create(request, key);
        assertEquals(first.id(), retry.id());
        assertNotEquals(first.guestAccessToken(), retry.guestAccessToken());
    }

    @Test
    void checkInIsAllowedOnlyFromThirtyMinutesBeforeStart() {
        Booking tooEarly = confirmedBookingAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(31));
        BusinessException error = assertThrows(BusinessException.class,
                () -> bookings.checkIn(tooEarly.getId()));
        assertEquals("ARRIVAL_TOO_EARLY", error.code());

        Booking earlyArrival = confirmedBookingAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(29));
        assertEquals("CHECKED_IN", bookings.checkIn(earlyArrival.getId()).status());
    }

    private Booking confirmedBookingAt(LocalDateTime startAt) {
        Booking booking = new Booking("PZ" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                court, "Khách thử", "0901234567", null, null, startAt, startAt.plusHours(2),
                BigDecimal.valueOf(240_000), BigDecimal.valueOf(2_000), "{}", "{}",
                LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30), 120, 30);
        booking.confirm();
        return bookingRecords.saveAndFlush(booking);
    }

    private BookingRequest request(Long courtId, OffsetDateTime from, OffsetDateTime to) {
        return new BookingRequest(courtId, "Khách thử", "0901234567", null, null, from, to);
    }
}
