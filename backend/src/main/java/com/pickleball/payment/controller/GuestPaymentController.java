package com.pickleball.payment.controller;

import com.pickleball.payment.dto.PaymentResponse;
import com.pickleball.payment.dto.RefundResponse;
import com.pickleball.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/guest")
public class GuestPaymentController {
    private final PaymentService payments;
    public GuestPaymentController(PaymentService payments) { this.payments = payments; }

    @PostMapping("/bookings/{code}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse forBooking(@PathVariable String code,
            @RequestHeader("X-Guest-Access-Token") String token,
            @Valid @RequestBody BookingPaymentRequest request) {
        return payments.createBookingPayment(code, token, request.purpose());
    }
    @GetMapping("/bookings/{code}/payments/{id}")
    public PaymentResponse bookingPayment(@PathVariable String code, @PathVariable Long id,
            @RequestHeader("X-Guest-Access-Token") String token) {
        return payments.bookingPayment(code, token, id);
    }
    @GetMapping("/bookings/{code}/payments")
    public List<PaymentResponse> bookingPayments(@PathVariable String code,
            @RequestHeader("X-Guest-Access-Token") String token) {
        return payments.bookingPayments(code, token);
    }
    @PostMapping("/orders/{code}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse forOrder(@PathVariable String code,
            @RequestHeader("X-Guest-Access-Token") String token) {
        return payments.createOrderPayment(code, token);
    }
    @GetMapping("/orders/{code}/payments/{id}")
    public PaymentResponse orderPayment(@PathVariable String code, @PathVariable Long id,
            @RequestHeader("X-Guest-Access-Token") String token) {
        return payments.orderPayment(code, token, id);
    }
    @GetMapping("/orders/{code}/payments")
    public List<PaymentResponse> orderPayments(@PathVariable String code,
            @RequestHeader("X-Guest-Access-Token") String token) {
        return payments.orderPayments(code, token);
    }
    @GetMapping("/bookings/{code}/refunds")
    public java.util.List<RefundResponse> bookingRefunds(@PathVariable String code,
            @RequestHeader("X-Guest-Access-Token") String token) {
        return payments.bookingRefunds(code, token).stream().map(RefundResponse::from).toList();
    }
    @GetMapping("/orders/{code}/refunds")
    public java.util.List<RefundResponse> orderRefunds(@PathVariable String code,
            @RequestHeader("X-Guest-Access-Token") String token) {
        return payments.orderRefunds(code, token).stream().map(RefundResponse::from).toList();
    }
    public record BookingPaymentRequest(@NotBlank String purpose) {}
}
