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
import com.pickleball.payment.repository.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public PaymentService(PaymentRepository payments, PaymentEventRepository events,
                          RefundRepository refunds, BookingService bookings, OrderService orders,
                          GuestAccessService access, List<PaymentProvider> providers,
                          @Value("${app.payment.sandbox-enabled:false}") boolean sandboxEnabled,
                          AuditService audit) {
        this.payments = payments; this.events = events; this.refunds = refunds;
        this.bookings = bookings; this.orders = orders; this.access = access;
        this.providers = providers; this.sandboxEnabled = sandboxEnabled;
        this.audit = audit;
    }

    @Transactional
    public PaymentResponse createBookingPayment(String code, String token, String purpose) {
        Booking booking = bookings.byCode(code);
        access.require(token, "BOOKING", booking.getId());
        BigDecimal amount;
        if ("DEPOSIT".equals(purpose) && "HOLD".equals(booking.getStatus())
                && booking.getHoldExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            amount = booking.getDepositRequired();
        } else if ("BALANCE".equals(purpose)
                && List.of("CONFIRMED", "CHECKED_IN").contains(booking.getStatus())) {
            amount = booking.getTotalAmount().subtract(succeededTotal(payments.findByBookingId(booking.getId())));
        } else throw conflict("INVALID_PAYMENT_PURPOSE", "Trạng thái phiếu không cho phép thu khoản này");
        if (amount.signum() <= 0) throw conflict("ALREADY_PAID", "Khoản này đã được thanh toán");
        return create(new Payment(booking, null, purpose, "ONLINE", provider().code(), amount));
    }

    @Transactional
    public PaymentResponse createOrderPayment(String code, String token) {
        StoreOrder order = orders.byCode(code);
        access.require(token, "ORDER", order.getId());
        if (List.of("CANCELLED", "COMPLETED").contains(order.getStatus()))
            throw conflict("ORDER_CLOSED", "Đơn đã đóng");
        BigDecimal amount = order.getTotalAmount().subtract(succeededTotal(payments.findByOrderId(order.getId())));
        if (amount.signum() <= 0) throw conflict("ALREADY_PAID", "Đơn đã được thanh toán");
        return create(new Payment(null, order, "ORDER", "ONLINE", provider().code(), amount));
    }

    @Transactional(readOnly = true)
    public PaymentResponse bookingPayment(String code, String token, Long id) {
        Booking booking = bookings.byCode(code);
        access.require(token, "BOOKING", booking.getId());
        Payment payment = byId(id);
        if (payment.getBooking() == null || !payment.getBooking().getId().equals(booking.getId()))
            throw forbidden();
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse orderPayment(String code, String token, Long id) {
        StoreOrder order = orders.byCode(code);
        access.require(token, "ORDER", order.getId());
        Payment payment = byId(id);
        if (payment.getOrder() == null || !payment.getOrder().getId().equals(order.getId()))
            throw forbidden();
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse sandboxComplete(Long id, String token, boolean success) {
        if (!sandboxEnabled) throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không có sandbox");
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
        audit.record("PAYMENT_MANUAL", "PAYMENT", payment.getId(), purpose + " " + method);
        return PaymentResponse.from(payment);
    }

    @Transactional
    public Refund refund(Long id, BigDecimal amount, String reason) {
        Payment payment = lock(id);
        if (!payment.getStatus().equals("SUCCEEDED") || amount == null || amount.signum() <= 0)
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

    private PaymentResponse create(Payment payment) {
        Payment saved = payments.save(payment);
        PaymentResponse response = PaymentResponse.from(saved);
        return new PaymentResponse(response.paymentId(), response.purpose(), response.method(),
                response.provider(), response.amount(), response.currency(), response.status(),
                response.createdAt(), response.paidAt(), provider().paymentUrl(saved));
    }

    private void settle(Payment payment, String eventId, String reference, boolean success) {
        if (events.existsByProviderAndEventId(payment.getProvider(), eventId)) return;
        if (!payment.getStatus().equals("PENDING")) return;
        if (success && payment.getBooking() != null && payment.getPurpose().equals("DEPOSIT"))
            bookings.confirmPaidDeposit(payment.getBooking().getId());
        if (success) payment.succeed(reference); else payment.fail();
        events.save(new PaymentEvent(payment.getProvider(), eventId, payment));
    }

    private PaymentProvider provider() {
        return providers.stream().findFirst().orElseThrow(() -> new BusinessException(
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
