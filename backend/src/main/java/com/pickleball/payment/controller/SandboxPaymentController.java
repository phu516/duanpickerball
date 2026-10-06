package com.pickleball.payment.controller;

import com.pickleball.payment.dto.PaymentResponse;
import com.pickleball.payment.service.PaymentService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("dev")
@RequestMapping("/api/v1/dev/payments")
public class SandboxPaymentController {
    private final PaymentService payments;
    public SandboxPaymentController(PaymentService payments) { this.payments = payments; }
    @PostMapping("/{id}/complete")
    public PaymentResponse complete(@PathVariable Long id,
            @RequestHeader("X-Guest-Access-Token") String token,
            @RequestBody Result request) {
        return payments.sandboxComplete(id, token, request.success());
    }
    public record Result(boolean success) {}
}
