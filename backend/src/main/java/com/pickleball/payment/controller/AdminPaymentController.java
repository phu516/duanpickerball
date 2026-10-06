package com.pickleball.payment.controller;

import com.pickleball.payment.dto.PaymentResponse;
import com.pickleball.payment.entity.Refund;
import com.pickleball.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/payments")
public class AdminPaymentController {
    private final PaymentService payments;
    public AdminPaymentController(PaymentService payments) { this.payments = payments; }
    @GetMapping public List<PaymentResponse> list() { return payments.all(); }
    @GetMapping("/{id}") public PaymentResponse detail(@PathVariable Long id) { return payments.detail(id); }
    @PostMapping("/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse manual(@Valid @RequestBody ManualRequest request) {
        return payments.manual(request.bookingId(), request.orderId(), request.purpose(),
                request.amount(), request.method(), request.reference());
    }
    @PostMapping("/{id}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse refund(@PathVariable Long id, @Valid @RequestBody RefundRequest request) {
        Refund refund = payments.refund(id, request.amount(), request.reason());
        return new RefundResponse(refund.getId(), refund.getAmount(), refund.getStatus(), refund.getReason());
    }
    public record ManualRequest(Long bookingId, Long orderId, @NotBlank String purpose,
                                @NotNull @DecimalMin("0.01") BigDecimal amount,
                                @NotBlank String method, String reference) {}
    public record RefundRequest(@NotNull @DecimalMin("0.01") BigDecimal amount,
                                @NotBlank String reason) {}
    public record RefundResponse(Long id, BigDecimal amount, String status, String reason) {}
}
