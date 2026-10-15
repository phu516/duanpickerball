package com.pickleball.booking.entity;

import com.pickleball.court.entity.Court;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String bookingCode;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id") private Court court;
    private Long userId;
    @Column(nullable = false) private String guestName;
    @Column(nullable = false) private String guestPhone;
    private String guestEmail;
    private String note;
    @Column(nullable = false) private LocalDateTime startAt;
    @Column(nullable = false) private LocalDateTime endAt;
    @Column(nullable = false) private BigDecimal totalAmount;
    @Column(nullable = false) private BigDecimal depositRequired;
    @Column(nullable = false, columnDefinition = "text") private String priceSnapshot;
    @Column(nullable = false) private String policySnapshot;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private LocalDateTime holdExpiresAt;
    @Column(nullable = false) private int minimumDurationMinutesSnapshot;
    @Column(nullable = false) private int lateArrivalGraceMinutesSnapshot;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private LocalDateTime noShowAt;
    private BigDecimal noShowFee;

    protected Booking() {}

    public Booking(String code, Court court, String name, String phone, String email, String note,
                   LocalDateTime startAt, LocalDateTime endAt, BigDecimal total,
                   BigDecimal deposit, String priceSnapshot, String policySnapshot,
                   LocalDateTime holdExpiresAt, int minimumMinutes, int arrivalGraceMinutes) {
        this.bookingCode = code;
        this.court = court;
        this.guestName = name;
        this.guestPhone = phone;
        this.guestEmail = email;
        this.note = note;
        this.startAt = startAt;
        this.endAt = endAt;
        this.totalAmount = total;
        this.depositRequired = deposit;
        this.priceSnapshot = priceSnapshot;
        this.policySnapshot = policySnapshot;
        this.holdExpiresAt = holdExpiresAt;
        this.minimumDurationMinutesSnapshot = minimumMinutes;
        this.lateArrivalGraceMinutesSnapshot = arrivalGraceMinutes;
        this.status = "HOLD";
        this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        this.updatedAt = createdAt;
    }

    public Long getId() { return id; }
    public String getBookingCode() { return bookingCode; }
    public Court getCourt() { return court; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getGuestName() { return guestName; }
    public String getGuestPhone() { return guestPhone; }
    public String getGuestEmail() { return guestEmail; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getDepositRequired() { return depositRequired; }
    public String getStatus() { return status; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public LocalDateTime getHoldExpiresAt() { return holdExpiresAt; }
    public int getLateArrivalGraceMinutesSnapshot() { return lateArrivalGraceMinutesSnapshot; }

    public boolean blocks(LocalDateTime now) {
        return switch (status) {
            case "HOLD" -> holdExpiresAt.isAfter(now);
            case "CONFIRMED", "CHECKED_IN", "NO_SHOW" -> true;
            default -> false;
        };
    }

    public void confirm() { transition("CONFIRMED"); }
    public void checkIn() { transition("CHECKED_IN"); }
    public void complete() { transition("COMPLETED"); }
    public void expire() { transition("EXPIRED"); }
    public void cancel(String reason) {
        transition("CANCELLED");
        cancelReason = reason;
        cancelledAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }
    public void markNoShow() {
        transition("NO_SHOW");
        noShowAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        noShowFee = depositRequired;
    }

    private void transition(String next) {
        status = next;
        updatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }
}
