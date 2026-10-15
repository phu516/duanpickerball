package com.pickleball.payment.entity;

import com.pickleball.booking.entity.Booking;
import com.pickleball.order.entity.StoreOrder;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "payments")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_id") private StoreOrder order;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "booking_id") private Booking booking;
    @Column(nullable = false) private String purpose;
    @Column(nullable = false) private String method;
    @Column(nullable = false) private String provider;
    private String providerReference;
    @Column(unique = true) private Long providerOrderCode;
    @Column(unique = true, length = 120) private String providerPaymentLinkId;
    @Column(length = 1000) private String paymentUrl;
    @Column(length = 2500) private String qrCode;
    @Column(length = 255) private String accountName;
    @Column(length = 60) private String accountNumber;
    @Column(length = 100) private String description;
    private LocalDateTime expiresAt;
    @Column(nullable = false) private BigDecimal amount;
    @Column(nullable = false) private String currency = "VND";
    @Column(nullable = false) private String status = "PENDING";
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
    private LocalDateTime paidAt;

    protected Payment() {}
    public Payment(Booking booking, StoreOrder order, String purpose, String method, String provider,
                   BigDecimal amount) {
        if ((booking == null) == (order == null)) throw new IllegalArgumentException("Payment owner required");
        this.booking = booking;
        this.order = order;
        this.purpose = purpose;
        this.method = method;
        this.provider = provider;
        this.amount = amount;
    }
    public Long getId() { return id; }
    public Booking getBooking() { return booking; }
    public StoreOrder getOrder() { return order; }
    public String getPurpose() { return purpose; }
    public String getMethod() { return method; }
    public String getProvider() { return provider; }
    public String getStatus() { return status; }
    public String getProviderReference() { return providerReference; }
    public Long getProviderOrderCode() { return providerOrderCode; }
    public String getProviderPaymentLinkId() { return providerPaymentLinkId; }
    public String getPaymentUrl() { return paymentUrl; }
    public String getQrCode() { return qrCode; }
    public String getAccountName() { return accountName; }
    public String getAccountNumber() { return accountNumber; }
    public String getDescription() { return description; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setCheckout(Long orderCode, String linkId, String url, String qr,
                            String accountName, String accountNumber, String description,
                            LocalDateTime expiresAt) {
        this.providerOrderCode = orderCode;
        this.providerPaymentLinkId = linkId;
        this.paymentUrl = url;
        this.qrCode = qr;
        this.accountName = accountName;
        this.accountNumber = accountNumber;
        this.description = description;
        this.expiresAt = expiresAt;
    }
    public void succeed(String reference) {
        status = "SUCCEEDED";
        providerReference = reference;
        paidAt = LocalDateTime.now(ZoneOffset.UTC);
    }
    public void fail() { status = "FAILED"; }
    public void review(String reference) {
        status = "REVIEW_REQUIRED";
        providerReference = reference;
        paidAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
