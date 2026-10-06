package com.pickleball.order.entity;

import com.pickleball.auth.entity.UserAccount;
import com.pickleball.booking.entity.Booking;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class StoreOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String orderCode;
    private Long userId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id") private Booking booking;
    @Column(nullable = false) private String guestName;
    @Column(nullable = false) private String guestPhone;
    private String guestEmail;
    @Column(nullable = false) private String fulfillmentMethod;
    private String deliveryAddress;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_staff_id") private UserAccount assignedStaff;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private BigDecimal subtotal;
    @Column(nullable = false) private BigDecimal deliveryFee;
    @Column(nullable = false) private BigDecimal totalAmount;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    private LocalDateTime deliveredAt;

    protected StoreOrder() {}

    public StoreOrder(String code, Booking booking, String name, String phone, String email,
                      String method, String address, BigDecimal subtotal, BigDecimal deliveryFee) {
        this.orderCode = code;
        this.booking = booking;
        this.guestName = name;
        this.guestPhone = phone;
        this.guestEmail = email;
        this.fulfillmentMethod = method;
        this.deliveryAddress = address;
        this.subtotal = subtotal;
        this.deliveryFee = deliveryFee;
        this.totalAmount = subtotal.add(deliveryFee);
        this.status = "NEW";
        this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        this.updatedAt = createdAt;
    }

    public Long getId() { return id; }
    public String getOrderCode() { return orderCode; }
    public Booking getBooking() { return booking; }
    public String getGuestName() { return guestName; }
    public String getGuestPhone() { return guestPhone; }
    public String getGuestEmail() { return guestEmail; }
    public String getFulfillmentMethod() { return fulfillmentMethod; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public UserAccount getAssignedStaff() { return assignedStaff; }
    public String getStatus() { return status; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void assign(UserAccount staff) { assignedStaff = staff; updatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC); }
    public void changeStatus(String next) {
        status = next;
        updatedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
        if (next.equals("DELIVERED") || next.equals("COMPLETED")) deliveredAt = updatedAt;
    }
}
