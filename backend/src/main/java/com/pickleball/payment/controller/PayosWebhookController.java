package com.pickleball.payment.controller;

import com.pickleball.payment.provider.PayosPaymentProvider;
import com.pickleball.payment.service.PaymentService;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.payos.model.webhooks.WebhookData;

@RestController
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "PAYOS")
public class PayosWebhookController {
    private final PayosPaymentProvider provider;
    private final PaymentService payments;

    public PayosWebhookController(PayosPaymentProvider provider, PaymentService payments) {
        this.provider = provider;
        this.payments = payments;
    }

    @PostMapping("/api/v1/webhooks/payos")
    public Map<String, Boolean> receive(@RequestBody Map<String, Object> body) {
        WebhookData data = provider.verifyWebhook(body);
        payments.receivePayosWebhook(data);
        return Map.of("success", true);
    }
}
