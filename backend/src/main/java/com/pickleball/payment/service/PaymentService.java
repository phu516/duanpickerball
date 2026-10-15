package com.pickleball.payment.service;

import com.pickleball.audit.service.AuditService;
import com.pickleball.booking.entity.Booking;
import com.pickleball.booking.service.BookingService;
import com.pickleball.common.exception.BusinessException;
import com.pickleball.common.security.GuestAccessService;
import com.pickleball.order.entity.StoreOrder;
import com.pickleball.order.service.OrderService;
import com.pickleball.payment.dto.PaymentResponse;
import com.pickleball.payment.entity.*;
import com.pickleball.payment.provider.PaymentProvider;
import com.pickleball.payment.provider.PaymentCheckout;
import com.pickleball.payment.provider.PayosPaymentProvider;
import com.pickleball.payment.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Comparator;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.webhooks.WebhookData;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final PaymentEventRepository events;
    private final RefundRepository refunds;
    private final BookingService bookings;
    private final OrderService orders;
    private final GuestAccessService access;
    private final List<PaymentProvider> providers;
    private final boolean sandboxEnabled;
    private final AuditService audit;
    private final EntityManager entityManager;
    private final String providerCode;
    private final String frontendOrigin;
    private final PaymentLinkLifecycleService paymentLinks;

    public PaymentService(PaymentRepository payments, PaymentEventRepository events,
                          RefundRepository refunds, BookingService bookings, OrderService orders,
                          GuestAccessService access, List<PaymentProvider> providers,
                          @Value("${app.payment.sandbox-enabled:false}") boolean sandboxEnabled,
                          @Value("${app.payment.provider:SANDBOX}") String providerCode,
                          @Value("${app.frontend-origin}") String frontendOrigin,
                          AuditService audit, EntityManager entityManager,
                          PaymentLinkLifecycleService paymentLinks) {
        this.payments = payments; this.events = events; this.refunds = refunds;
        this.bookings = bookings; this.orders = orders; this.access = access;
        this.providers = providers; this.sandboxEnabled = sandboxEnabled;
        this.audit = audit; this.entityManager = entityManager;
        this.providerCode = providerCode; this.frontendOrigin = frontendOrigin;
        this.paymentLinks = paymentLinks;
    }

    @Transactional
    public PaymentResponse createBookingPayment(String code, String token, String purpose) {
        Booking booking = bookings.byCode(code);
        access.require(token, "BOOKING", booking.getId());
        entityManager.lock(booking, LockModeType.PESSIMISTIC_WRITE);
        BigDecimal amount;
        if ("DEPOSIT".equals(purpose) && "HOLD".equals(booking.getStatus())
                && booking.getHoldExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            amount = booking.getDepositRequired();
        } else throw conflict("INVALID_PAYMENT_PURPOSE", "Trạng thái phiếu không cho phép thu khoản này");
        if (amount.signum() <= 0) throw conflict("ALREADY_PAID", "Khoản này đã được thanh toán");
        PaymentResponse existing = pending(payments.findByBookingId(booking.getId()), purpose, amount);
        if (existing != null) return existing;
        return create(new Payment(booking, null, purpose, "ONLINE", provider().code(), amount),
                booking.getHoldExpiresAt(), "booking", code);
    }

    @Transactional
    public PaymentResponse createOrderPayment(String code, String token) {
        StoreOrder order = orders.byCode(code);
        access.require(token, "ORDER", order.getId());
        entityManager.lock(order, LockModeType.PESSIMISTIC_WRITE);
        if (List.of("CANCELLED", "COMPLETED").contains(order.getStatus()))
            throw conflict("ORDER_CLOSED", "Đơn đã đóng");
        BigDecimal amount = order.getTotalAmount().subtract(succeededTotal(payments.findByOrderId(order.getId())));
        if (amount.signum() <= 0) throw conflict("ALREADY_PAID", "Đơn đã được thanh toán");
        PaymentResponse existing = pending(payments.findByOrderId(order.getId()), "ORDER", amount);
        if (existing != null) return existing;
        return create(new Payment(null, order, "ORDER", "ONLINE", provider().code(), amount),
                LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30), "order", code);
    }

    @Transactional
    public PaymentResponse bookingPayment(String code, String token, Long id) {
        Booking booking = bookings.byCode(code);
        access.require(token, "BOOKING", booking.getId());
        Payment payment = lock(id);
        if (payment.getBooking() == null || !payment.getBooking().getId().equals(booking.getId()))
            throw forbidden();
        syncPayos(payment);
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> bookingPayments(String code, String token) {
        Booking booking = bookings.byCode(code);
        access.require(token, "BOOKING", booking.getId());
        return payments.findByBookingId(booking.getId()).stream()
                .sorted(Comparator.comparing(Payment::getId).reversed())
                .map(PaymentResponse::from).toList();
    }

    @Transactional
    public PaymentResponse orderPayment(String code, String token, Long id) {
        StoreOrder order = orders.byCode(code);
        access.require(token, "ORDER", order.getId());
        Payment payment = lock(id);
        if (payment.getOrder() == null || !payment.getOrder().getId().equals(order.getId()))
            throw forbidden();
        syncPayos(payment);
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> orderPayments(String code, String token) {
        StoreOrder order = orders.byCode(code);
        access.require(token, "ORDER", order.getId());
        return payments.findByOrderId(order.getId()).stream()
                .sorted(Comparator.comparing(Payment::getId).reversed())
                .map(PaymentResponse::from).toList();
    }

    @Transactional
    public PaymentResponse sandboxComplete(Long id, String token, boolean success) {
        if (!sandboxEnabled || !"SANDBOX".equals(providerCode))
            throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không có sandbox");
        Payment payment = lock(id);
        if (payment.getBooking() != null) access.require(token, "BOOKING", payment.getBooking().getId());
        else access.require(token, "ORDER", payment.getOrder().getId());
        if (!payment.getProvider().equals("SANDBOX")) throw forbidden();
        settle(payment, "sandbox-" + id, "sbx-" + id, success);
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse manual(Long bookingId, Long orderId, String purpose, BigDecimal amount,
                                  String method, String reference) {
        if ((bookingId == null) == (orderId == null) || amount == null || amount.signum() <= 0)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_MANUAL_PAYMENT", "Chọn đúng một phiếu hoặc đơn và số tiền dương");
        Booking booking = bookingId == null ? null : bookings.byId(bookingId);
        StoreOrder order = orderId == null ? null : orders.byId(orderId);
        if (booking != null && !List.of("DEPOSIT", "BALANCE").contains(purpose))
            throw conflict("INVALID_PAYMENT_PURPOSE", "Mục đích thu sân không hợp lệ");
        if (!List.of("CASH", "BANK_TRANSFER").contains(method))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_METHOD", "Phương thức thủ công không hợp lệ");
        if (booking != null && "DEPOSIT".equals(purpose)
                && amount.compareTo(booking.getDepositRequired()) != 0)
            throw conflict("DEPOSIT_AMOUNT_MISMATCH", "Số tiền cọc phải đúng theo phiếu");
        if (booking != null && "BALANCE".equals(purpose)
                && !List.of("CONFIRMED", "CHECKED_IN").contains(booking.getStatus()))
            throw conflict("INVALID_PAYMENT_PURPOSE", "Chỉ thu phần còn lại sau khi phiếu xác nhận");
        if (order != null && !"ORDER".equals(purpose))
            throw conflict("INVALID_PAYMENT_PURPOSE", "Khoản thu của đơn không hợp lệ");
        BigDecimal outstanding = booking == null
                ? order.getTotalAmount().subtract(succeededTotal(payments.findByOrderId(orderId)))
                : booking.getTotalAmount().subtract(succeededTotal(payments.findByBookingId(bookingId)));
        if (amount.compareTo(outstanding) > 0) throw conflict("OVERPAYMENT", "Số tiền vượt khoản phải thu");
        Payment payment = payments.save(new Payment(booking, order, purpose, method, "MANUAL", amount));
        settle(payment, "manual-" + payment.getId(), reference, true);
        if (booking != null) paymentLinks.cancelForBooking(booking.getId());
        else paymentLinks.cancelForOrder(order.getId());
        audit.record("PAYMENT_MANUAL", "PAYMENT", payment.getId(), purpose + " " + method);
        return PaymentResponse.from(payment);
    }

    @Transactional
    public Refund refund(Long id, BigDecimal amount, String reason) {
        Payment payment = lock(id);
        if (!List.of("SUCCEEDED", "REVIEW_REQUIRED").contains(payment.getStatus())
                || amount == null || amount.signum() <= 0)
            throw conflict("INVALID_REFUND", "Chỉ hoàn tiền đã thu với số tiền dương");
        BigDecimal refunded = refunds.findByPaymentId(id).stream()
                .filter(r -> !r.getStatus().equals("FAILED"))
                .map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (refunded.add(amount).compareTo(payment.getAmount()) > 0)
            throw conflict("REFUND_EXCEEDS_PAYMENT", "Số tiền hoàn vượt số đã thu");
        Refund refund = refunds.save(new Refund(payment, amount, reason));
        audit.record("REFUND_REQUESTED", "REFUND", refund.getId(), reason);
        return refund;
    }

    @Transactional(readOnly = true)
    public List<Refund> refunds() { return refunds.findAll(); }

    @Transactional(readOnly = true)
    public Refund refundDetail(Long id) { return refunds.findById(id).orElseThrow(() -> new BusinessException(
            HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND", "Không tìm thấy khoản hoàn")); }

    @Transactional
    public Refund confirmManualRefund(Long id, String reference) {
        Refund refund = refundDetail(id);
        if (!refund.getStatus().equals("PENDING") || !refund.getPayment().getProvider().equals("MANUAL"))
            throw conflict("REFUND_NOT_MANUAL", "Chỉ xác nhận khoản hoàn thủ công đang chờ");
        if (reference == null || reference.isBlank())
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REFERENCE_REQUIRED", "Cần mã chứng từ hoàn tiền");
        refund.complete(reference);
        audit.record("REFUND_CONFIRMED", "REFUND", id, reference);
        return refund;
    }

    @Transactional(readOnly = true)
    public List<Refund> bookingRefunds(String code, String token) {
        Booking booking = bookings.byCode(code);
        access.require(token, "BOOKING", booking.getId());
        return payments.findByBookingId(booking.getId()).stream()
                .flatMap(payment -> refunds.findByPaymentId(payment.getId()).stream()).toList();
    }

    @Transactional(readOnly = true)
    public List<Refund> orderRefunds(String code, String token) {
        StoreOrder order = orders.byCode(code);
        access.require(token, "ORDER", order.getId());
        return payments.findByOrderId(order.getId()).stream()
                .flatMap(payment -> refunds.findByPaymentId(payment.getId()).stream()).toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> all() { return payments.findAll().stream().map(PaymentResponse::from).toList(); }
    @Transactional(readOnly = true)
    public PaymentResponse detail(Long id) { return PaymentResponse.from(byId(id)); }

    private PaymentResponse create(Payment payment, LocalDateTime requestedExpiry,
                                   String type, String code) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if ("DEPOSIT".equals(payment.getPurpose()) && !requestedExpiry.isAfter(now))
            throw conflict("HOLD_EXPIRED", "Phiếu giữ sân đã hết hạn");
        LocalDateTime expiry = requestedExpiry.isAfter(now) ? requestedExpiry : now.plusMinutes(30);
        Payment saved = payments.saveAndFlush(payment);
        String returnUrl = frontendOrigin.replaceAll("/+$", "")
                + "/?paymentType=" + type + "&resourceCode=" + code;
        PaymentCheckout checkout = provider().createCheckout(saved, expiry, returnUrl);
        saved.setCheckout(checkout.orderCode(), checkout.paymentLinkId(), checkout.paymentUrl(),
                checkout.qrCode(), checkout.accountName(), checkout.accountNumber(),
                checkout.description(), expiry);
        return PaymentResponse.from(saved);
    }

    private PaymentResponse pending(List<Payment> list, String purpose, BigDecimal amount) {
        for (Payment payment : list) {
            if (!payment.getStatus().equals("PENDING") || !payment.getPurpose().equals(purpose)
                    || !payment.getProvider().equals(providerCode)) continue;
            syncPayos(payment);
            if (!payment.getStatus().equals("PENDING")) return PaymentResponse.from(payment);
            if (payment.getExpiresAt() != null
                    && payment.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))
                    && payment.getAmount().compareTo(amount) == 0) return PaymentResponse.from(payment);
            provider().cancelCheckout(payment);
            payment.fail();
        }
        return null;
    }

    @Transactional
    public void receivePayosWebhook(WebhookData data) {
        if (data == null || data.getOrderCode() == null || data.getAmount() == null
                || data.getAmount() <= 0)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PAYOS_WEBHOOK_INVALID",
                    "Dữ liệu giao dịch payOS không hợp lệ");
        Payment payment = payments.findLockedPayosOrder(data.getOrderCode()).orElse(null);
        if (payment == null) {
            if (data.getOrderCode() >= 1_000_000_000_000L)
                throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "PAYOS_PAYMENT_NOT_READY",
                        "Chưa tìm thấy giao dịch; payOS cần gửi lại webhook");
            return; // payOS sends a signed sample when registering the webhook URL.
        }
        if (!Objects.equals(payment.getProviderPaymentLinkId(), data.getPaymentLinkId()))
            throw conflict("PAYOS_LINK_MISMATCH", "Mã link payOS không khớp");
        String eventId = data.getReference();
        if (eventId == null || eventId.isBlank())
            eventId = data.getPaymentLinkId() + ":" + data.getTransactionDateTime();
        if (events.existsByProviderAndEventId("PAYOS", eventId)) return;
        if ("REVIEW_REQUIRED".equals(payment.getStatus())
                && Objects.equals(payment.getProviderReference(), eventId)) return;
        if (!"00".equals(data.getCode())) return;
        if (!"VND".equals(data.getCurrency())
                || payment.getAmount().compareTo(BigDecimal.valueOf(data.getAmount())) != 0) {
            Payment review = "PENDING".equals(payment.getStatus()) ? payment
                    : payments.save(new Payment(payment.getBooking(), payment.getOrder(),
                            payment.getPurpose(), "ONLINE", "PAYOS", BigDecimal.valueOf(data.getAmount())));
            review.review(eventId);
            events.save(new PaymentEvent("PAYOS", eventId, review));
            audit.record("PAYOS_AMOUNT_REVIEW", "PAYMENT", review.getId(), "Số tiền hoặc tiền tệ không khớp");
            return;
        }
        settle(payment, eventId, eventId, true);
    }

    private void syncPayos(Payment payment) {
        if (!"PAYOS".equals(payment.getProvider()) || !"PENDING".equals(payment.getStatus())
                || payment.getProviderOrderCode() == null) return;
        if (!(provider() instanceof PayosPaymentProvider payos)) return;
        try {
            PaymentLink link = payos.getPaymentLink(payment);
            if ("PAID".equals(link.getStatus().name())) {
                String reference = link.getTransactions() == null || link.getTransactions().isEmpty()
                        ? "payos-" + payment.getProviderOrderCode()
                        : link.getTransactions().get(0).getReference();
                if (reference == null || reference.isBlank())
                    reference = "payos-" + payment.getProviderOrderCode();
                if (payment.getAmount().compareTo(BigDecimal.valueOf(link.getAmountPaid())) != 0) {
                    payment.review(reference);
                    audit.record("PAYOS_AMOUNT_REVIEW", "PAYMENT", payment.getId(), "Số tiền payOS không khớp");
                } else settle(payment, reference, reference, true);
            } else if ("CANCELLED".equals(link.getStatus().name())
                    || "EXPIRED".equals(link.getStatus().name())
                    || payment.getExpiresAt() != null && !payment.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                payos.cancelCheckout(payment);
                payment.fail();
            }
        } catch (BusinessException ignored) {
            // The signed webhook can still update a pending payment while payOS is unavailable.
        }
    }

    private void settle(Payment payment, String eventId, String reference, boolean success) {
        if (events.existsByProviderAndEventId(payment.getProvider(), eventId)) return;
        if (!payment.getStatus().equals("PENDING")) {
            if (success && "PAYOS".equals(payment.getProvider())
                    && List.of("SUCCEEDED", "REVIEW_REQUIRED").contains(payment.getStatus())) {
                Payment extra = payments.save(new Payment(payment.getBooking(), payment.getOrder(),
                        payment.getPurpose(), "ONLINE", "PAYOS", payment.getAmount()));
                extra.review(reference);
                audit.record("PAYOS_ADDITIONAL_TRANSFER", "PAYMENT", extra.getId(), "Cần đối soát/hoàn giao dịch thêm");
                events.save(new PaymentEvent("PAYOS", eventId, extra));
            } else if (success && "PAYOS".equals(payment.getProvider())) {
                payment.review(reference);
                audit.record("PAYOS_LATE_PAYMENT", "PAYMENT", payment.getId(), "Tiền đến sau khi link đóng");
                events.save(new PaymentEvent(payment.getProvider(), eventId, payment));
            }
            return;
        }
        if (success && "PAYOS".equals(payment.getProvider()) &&
                (payment.getExpiresAt() == null || !payment.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))
                || payment.getOrder() != null && "CANCELLED".equals(payment.getOrder().getStatus())
                || payment.getBooking() != null && "BALANCE".equals(payment.getPurpose())
                    && "CANCELLED".equals(payment.getBooking().getStatus()))) {
            payment.review(reference);
            audit.record("PAYOS_LATE_PAYMENT", "PAYMENT", payment.getId(), "Giao dịch cần đối soát/hoàn");
        } else if (success && payment.getBooking() != null && payment.getPurpose().equals("DEPOSIT")) {
            Booking booking = payment.getBooking();
            if ("PAYOS".equals(payment.getProvider())
                    && (!"HOLD".equals(booking.getStatus())
                    || !booking.getHoldExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)))) {
                payment.review(reference);
                audit.record("PAYOS_LATE_PAYMENT", "PAYMENT", payment.getId(), "Phiếu đã hết hạn giữ sân");
            } else {
                bookings.confirmPaidDeposit(booking.getId());
                payment.succeed(reference);
            }
        } else if (success) payment.succeed(reference);
        else payment.fail();
        events.save(new PaymentEvent(payment.getProvider(), eventId, payment));
    }

    private PaymentProvider provider() {
        return providers.stream().filter(value -> value.code().equals(providerCode)).findFirst()
                .orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_IMPLEMENTED, "PAYMENT_PROVIDER_MISSING", "Chưa cấu hình cổng thanh toán"));
    }
    private BigDecimal succeededTotal(List<Payment> list) {
        return list.stream().filter(p -> p.getStatus().equals("SUCCEEDED"))
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private Payment byId(Long id) { return payments.findById(id).orElseThrow(() -> new BusinessException(
            HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Không tìm thấy khoản thu")); }
    private Payment lock(Long id) { return payments.findLockedById(id).orElseThrow(() -> new BusinessException(
            HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Không tìm thấy khoản thu")); }
    private BusinessException forbidden() { return new BusinessException(HttpStatus.FORBIDDEN, "PAYMENT_FORBIDDEN", "Không có quyền xem khoản thu"); }
    private BusinessException conflict(String code, String message) { return new BusinessException(HttpStatus.CONFLICT, code, message); }
}
