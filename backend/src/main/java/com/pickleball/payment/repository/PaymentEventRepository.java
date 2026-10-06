package com.pickleball.payment.repository;

import com.pickleball.payment.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {
    boolean existsByProviderAndEventId(String provider, String eventId);
}
