package com.pickleball.payment.controller;

import com.pickleball.payment.dto.RefundResponse;
import com.pickleball.payment.service.PaymentService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/refunds")
public class AdminRefundController {
    private final PaymentService payments;
    public AdminRefundController(PaymentService payments) { this.payments = payments; }
    @GetMapping public List<RefundResponse> list() {
        return payments.refunds().stream().map(RefundResponse::from).toList();
    }
    @GetMapping("/{id}") public RefundResponse detail(@PathVariable Long id) {
        return RefundResponse.from(payments.refundDetail(id));
    }
    @PostMapping("/{id}/confirm-manual")
    public RefundResponse confirmManual(@PathVariable Long id, @RequestBody Reference request) {
        return RefundResponse.from(payments.confirmManualRefund(id, request.reference()));
    }
    public record Reference(String reference) {}
}
