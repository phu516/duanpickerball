package com.pickleball.payment.provider;

import com.pickleball.common.exception.BusinessException;
import com.pickleball.payment.entity.Payment;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.webhooks.WebhookData;

@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "PAYOS")
public class PayosPaymentProvider implements PaymentProvider {
    private final PayOS client;

    public PayosPaymentProvider(@Value("${app.payment.payos.client-id}") String clientId,
                                @Value("${app.payment.payos.api-key}") String apiKey,
                                @Value("${app.payment.payos.checksum-key}") String checksumKey) {
        if (clientId.isBlank() || apiKey.isBlank() || checksumKey.isBlank()) {
            throw new IllegalStateException("Thiếu cấu hình payOS trong backend");
        }
        this.client = new PayOS(clientId, apiKey, checksumKey);
    }

    @Override public String code() { return "PAYOS"; }

    @Override
    public PaymentCheckout createCheckout(Payment payment, LocalDateTime expiresAt, String returnUrl) {
        long orderCode = 1_000_000_000_000L + payment.getId();
        String description = "PZ" + Long.toString(payment.getId(), 36).toUpperCase();
        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(payment.getAmount().longValueExact())
                .description(description)
                .returnUrl(returnUrl)
                .cancelUrl(returnUrl)
                .expiredAt(expiresAt.toEpochSecond(ZoneOffset.UTC))
                .build();
        try {
            CreatePaymentLinkResponse result = client.paymentRequests().create(request);
            if (!Long.valueOf(orderCode).equals(result.getOrderCode())
                    || result.getAmount() != payment.getAmount().longValueExact()) {
                throw new IllegalStateException("payOS trả về giao dịch không khớp");
            }
            return new PaymentCheckout(orderCode, result.getPaymentLinkId(), result.getCheckoutUrl(),
                    result.getQrCode(), result.getAccountName(), result.getAccountNumber(),
                    result.getDescription());
        } catch (RuntimeException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "PAYOS_UNAVAILABLE",
                    "Không tạo được mã thanh toán payOS. Vui lòng thử lại.");
        }
    }

    @Override
    public void cancelCheckout(Payment payment) {
        if (payment.getProviderPaymentLinkId() == null) return;
        try {
            client.paymentRequests().cancel(payment.getProviderPaymentLinkId(), "Phiếu hoặc link hết hạn");
        } catch (RuntimeException ignored) {
            // The expiry is enforced by payOS; a later webhook is still reconciled.
        }
    }

    public WebhookData verifyWebhook(Map<String, Object> body) {
        try {
            return client.webhooks().verify(body);
        } catch (RuntimeException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PAYOS_SIGNATURE_INVALID",
                    "Chữ ký webhook payOS không hợp lệ");
        }
    }

    public PaymentLink getPaymentLink(Payment payment) {
        try {
            return client.paymentRequests().get(payment.getProviderOrderCode());
        } catch (RuntimeException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "PAYOS_UNAVAILABLE",
                    "Chưa kiểm tra được giao dịch payOS. Vui lòng thử lại.");
        }
    }
}
