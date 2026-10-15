package com.pickleball.booking.repository;

import com.pickleball.booking.entity.Booking;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findAllByOrderByCreatedAtDescIdDesc();
    @Query("select b from Booking b where exists (select p.id from Payment p where p.booking = b and p.purpose = 'DEPOSIT' and p.status = 'SUCCEEDED') order by b.createdAt desc, b.id desc")
    List<Booking> findAllWithSuccessfulDeposit();
    @Query("select b from Booking b where b.status = 'CANCELLED' or exists (select p.id from Payment p where p.booking = b and p.purpose = 'DEPOSIT' and p.status = 'SUCCEEDED') order by b.createdAt desc, b.id desc")
    List<Booking> findAllVisibleToAdmin();
    @Query("select b from Booking b join fetch b.court where b.status = 'CANCELLED' order by b.cancelledAt desc")
    List<Booking> findCancelledBookings();
    Optional<Booking> findByBookingCode(String code);
    long countByBookingCodeStartingWith(String prefix);
    List<Booking> findByCourtIdAndStartAtLessThanAndEndAtGreaterThan(
            Long courtId, LocalDateTime end, LocalDateTime start);
    List<Booking> findByStatusAndHoldExpiresAtBefore(String status, LocalDateTime now);
    List<Booking> findByStartAtBetween(LocalDateTime start, LocalDateTime end);
}
