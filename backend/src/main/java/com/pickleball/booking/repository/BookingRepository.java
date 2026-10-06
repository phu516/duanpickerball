package com.pickleball.booking.repository;

import com.pickleball.booking.entity.Booking;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByBookingCode(String code);
    long countByBookingCodeStartingWith(String prefix);
    List<Booking> findByCourtIdAndStartAtLessThanAndEndAtGreaterThan(
            Long courtId, LocalDateTime end, LocalDateTime start);
    List<Booking> findByStatusAndHoldExpiresAtBefore(String status, LocalDateTime now);
    List<Booking> findByStartAtBetween(LocalDateTime start, LocalDateTime end);
}
