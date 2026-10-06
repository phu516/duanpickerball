package com.pickleball.booking.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking_cancellation_requests")
public class BookingCancellationRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id") private Booking booking;
    @Column(nullable = false) private String reason;
    @Column(nullable = false) private String status;
    private Long reviewedBy;
    private String reviewReason;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    protected BookingCancellationRequest() {}

    public BookingCancellationRequest(Booking booking, String reason) {
        this.booking = booking;
        this.reason = reason;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Booking getBooking() { return booking; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void review(Long actorId, String nextStatus, String reason) {
        this.reviewedBy = actorId;
        this.reviewReason = reason;
        this.status = nextStatus;
        this.reviewedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }
}
