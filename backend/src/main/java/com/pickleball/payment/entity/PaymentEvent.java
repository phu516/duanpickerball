package com.pickleball.payment.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "payment_events", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "event_id"}))
public class PaymentEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String provider;
    @Column(nullable = false) private String eventId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_id", nullable = false) private Payment payment;
    @Column(nullable = false) private LocalDateTime receivedAt = LocalDateTime.now(ZoneOffset.UTC);
    protected PaymentEvent() {}
    public PaymentEvent(String provider, String eventId, Payment payment) {
        this.provider = provider; this.eventId = eventId; this.payment = payment;
    }
}
