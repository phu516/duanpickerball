package com.pickleball.payment;

import com.pickleball.booking.dto.BookingRequest;
import com.pickleball.booking.dto.BookingResponse;
import com.pickleball.booking.service.BookingService;
import com.pickleball.common.exception.BusinessException;
import com.pickleball.court.entity.Court;
import com.pickleball.court.entity.CourtPriceRule;
import com.pickleball.court.repository.CourtPriceRuleRepository;
import com.pickleball.court.repository.CourtRepository;
import com.pickleball.payment.entity.Payment;
import com.pickleball.payment.repository.PaymentEventRepository;
import com.pickleball.payment.repository.PaymentRepository;
import com.pickleball.payment.service.PaymentService;
import com.pickleball.payment.provider.PayosPaymentProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import vn.payos.model.webhooks.WebhookData;
import vn.payos.PayOS;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.payment.provider=PAYOS",
        "app.payment.payos.client-id=test-client",
        "app.payment.payos.api-key=test-api",
        "app.payment.payos.checksum-key=test-checksum"
})
@ActiveProfiles("test")
class PayosWebhookIntegrationTest {
    @Autowired CourtRepository courts;
    @Autowired CourtPriceRuleRepository rules;
    @Autowired BookingService bookings;
    @Autowired PaymentRepository payments;
    @Autowired PaymentEventRepository events;
    @Autowired PaymentService paymentService;
    @Autowired PayosPaymentProvider provider;

    @Test
    void webhookSignatureIsCheckedBeforePaymentIsAccepted() {
        BookingResponse booking = booking();
        Payment payment = pendingDeposit(booking, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15));
        WebhookData data = webhook(payment, "signed-" + UUID.randomUUID());
        PayOS signer = new PayOS("test-client", "test-api", "test-checksum");
        String signature = signer.getCrypto().createSignatureFromObj(data, "test-checksum");
        Map<String, Object> signed = Map.of("code", "00", "desc", "success", "success", true,
                "data", data, "signature", signature);
        assertEquals(payment.getProviderOrderCode(), provider.verifyWebhook(signed).getOrderCode());

        WebhookData changed = webhook(payment, data.getReference());
        changed.setAmount(changed.getAmount() + 1);
        Map<String, Object> tampered = Map.of("code", "00", "desc", "success", "success", true,
                "data", changed, "signature", signature);
        assertThrows(BusinessException.class, () -> provider.verifyWebhook(tampered));
    }

    @Test
    void signedPaymentEventConfirmsHoldOnlyOnce() {
        BookingResponse booking = booking();
        Payment payment = pendingDeposit(booking, LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15));
        long before = events.count();
        WebhookData data = webhook(payment, "reference-" + UUID.randomUUID());

        paymentService.receivePayosWebhook(data);
        paymentService.receivePayosWebhook(data);

        assertEquals("CONFIRMED", bookings.adminDetail(booking.id()).status());
        assertEquals("SUCCEEDED", payments.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(before + 1, events.count());
    }

    @Test
    void paidExpiredLinkIsRecordedForReviewWithoutConfirmingCourt() {
        BookingResponse booking = booking();
        Payment payment = pendingDeposit(booking, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        paymentService.receivePayosWebhook(webhook(payment, "late-" + UUID.randomUUID()));

        assertEquals("HOLD", bookings.adminDetail(booking.id()).status());
        assertEquals("REVIEW_REQUIRED", payments.findById(payment.getId()).orElseThrow().getStatus());
    }

    private BookingResponse booking() {
        Court court = courts.save(new Court("Q" + UUID.randomUUID().toString().substring(0, 8),
                "Sân webhook", "Acrylic", false, ""));
        for (int day = 1; day <= 7; day++) rules.save(new CourtPriceRule(court, day,
                LocalTime.of(6, 0), LocalTime.of(23, 0), BigDecimal.valueOf(100_000),
                LocalDate.now().minusDays(1), null));
        var from = LocalDate.now().plusDays(3).atTime(17, 0).atOffset(ZoneOffset.ofHours(7));
        return bookings.create(new BookingRequest(court.getId(), "Khách payOS", "0901234567",
                null, null, from, from.plusHours(2)));
    }

    private Payment pendingDeposit(BookingResponse booking, LocalDateTime expiresAt) {
        Payment payment = payments.saveAndFlush(new Payment(bookings.byId(booking.id()), null,
                "DEPOSIT", "ONLINE", "PAYOS", booking.depositRequired()));
        payment.setCheckout(1_000_000_000_000L + payment.getId(), "link-" + payment.getId(),
                "https://pay.payos.vn/example", "qr", "PickleZone", "123456789",
                "PZ" + payment.getId(), expiresAt);
        return payments.saveAndFlush(payment);
    }

    private WebhookData webhook(Payment payment, String reference) {
        return WebhookData.builder()
                .orderCode(payment.getProviderOrderCode())
                .amount(payment.getAmount().longValueExact())
                .description(payment.getDescription())
                .accountNumber(payment.getAccountNumber())
                .reference(reference)
                .transactionDateTime("2026-10-08 10:00:00")
                .currency("VND")
                .paymentLinkId(payment.getProviderPaymentLinkId())
                .code("00")
                .desc("success")
                .build();
    }
}
