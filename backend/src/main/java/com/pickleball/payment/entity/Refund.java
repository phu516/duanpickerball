package com.pickleball.payment.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "refunds")
public class Refund {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_id", nullable = false) private Payment payment;
    @Column(nullable = false) private BigDecimal amount;
    @Column(nullable = false) private String reason;
    @Column(nullable = false) private String status;
    private String providerReference;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
    private LocalDateTime completedAt;
    protected Refund() {}
    public Refund(Payment payment, BigDecimal amount, String reason) {
        this.payment = payment; this.amount = amount; this.reason = reason; this.status = "PENDING";
    }
    public Long getId() { return id; }
    public Payment getPayment() { return payment; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public void complete(String reference) {
        status = "SUCCEEDED"; providerReference = reference; completedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
