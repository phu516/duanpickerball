package com.pickleball.booking.repository;

import com.pickleball.booking.entity.BookingCancellationRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingCancellationRepository extends JpaRepository<BookingCancellationRequest, Long> {
    List<BookingCancellationRequest> findByStatusOrderByCreatedAtAsc(String status);
}
