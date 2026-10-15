package com.pickleball.booking.service;

import tools.jackson.databind.ObjectMapper;
import com.pickleball.audit.service.AuditService;
import com.pickleball.booking.dto.*;
import com.pickleball.booking.entity.*;
import com.pickleball.booking.repository.*;
import com.pickleball.common.exception.BusinessException;
import com.pickleball.common.idempotency.IdempotencyService;
import com.pickleball.common.security.GuestAccessService;
import com.pickleball.court.dto.BookingQuote;
import com.pickleball.court.entity.Court;
import com.pickleball.court.service.CourtService;
import com.pickleball.setting.service.BookingPolicyService;
import com.pickleball.payment.service.PaymentLinkLifecycleService;
import java.time.*;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private final BookingRepository bookings;
    private final BookingCancellationRepository cancellations;
    private final CourtService courts;
    private final GuestAccessService guestAccess;
    private final ObjectMapper mapper;
    private final BookingPolicyService policies;
    private final AuditService audit;
    private final IdempotencyService idempotency;
    private final PaymentLinkLifecycleService paymentLinks;

    public BookingService(BookingRepository bookings, BookingCancellationRepository cancellations,
                          CourtService courts, GuestAccessService guestAccess, ObjectMapper mapper,
                          BookingPolicyService policies, AuditService audit,
                          IdempotencyService idempotency, PaymentLinkLifecycleService paymentLinks) {
        this.bookings = bookings;
        this.cancellations = cancellations;
        this.courts = courts;
        this.guestAccess = guestAccess;
        this.mapper = mapper;
        this.policies = policies;
        this.audit = audit;
        this.idempotency = idempotency;
        this.paymentLinks = paymentLinks;
    }

    @Transactional
    public BookingResponse create(BookingRequest request) {
        return create(request, null);
    }

    @Transactional
    public BookingResponse create(BookingRequest request, String requestKey) {
        var previous = idempotency.previous("BOOKING", requestKey, request);
        if (previous.isPresent()) {
            Booking old = byId(previous.get());
            return BookingResponse.from(old, guestAccess.issue("BOOKING", old.getId(),
                    old.getEndAt().plusDays(30)));
        }
        Court court = courts.lock(request.courtId());
        BookingQuote quote = courts.quote(court.getId(), request.startAt(), request.endAt());
        String priceSnapshot = mapper.writeValueAsString(quote.segments());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String code = "PZ" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
        int holdMinutes = policies.get("booking.hold_minutes");
        int minimumMinutes = policies.get("booking.minimum_minutes");
        int arrivalGraceMinutes = policies.get("booking.arrival_grace_minutes");
        Booking booking = bookings.save(new Booking(code, court, request.name().trim(),
                request.phone().trim(), request.email(), request.note(),
                LocalDateTime.ofInstant(request.startAt().toInstant(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(request.endAt().toInstant(), ZoneOffset.UTC),
                quote.totalAmount(), quote.depositRequired(), priceSnapshot,
                "hold=" + holdMinutes + "m;arrival=" + arrivalGraceMinutes + "m;minimum=" + minimumMinutes + "m", now.plusMinutes(holdMinutes),
                minimumMinutes, arrivalGraceMinutes));
        idempotency.remember("BOOKING", requestKey, request, "BOOKING", booking.getId());
        return BookingResponse.from(booking, guestAccess.issue("BOOKING", booking.getId(),
                booking.getEndAt().plusDays(30)));
    }

    @Transactional(readOnly = true)
    public BookingResponse getGuest(String code, String token) {
        Booking booking = byCode(code);
        guestAccess.require(token, "BOOKING", booking.getId());
        return BookingResponse.from(booking, null);
    }

    @Transactional(readOnly = true)
    public List<CancellationResponse> adminCancellationRequests() {
        List<BookingCancellationRequest> requests = cancellations.findAll();
        Set<Long> approvedBookingIds = requests.stream()
                .filter(request -> request.getStatus().equals("APPROVED"))
                .map(request -> request.getBooking().getId())
                .collect(java.util.stream.Collectors.toSet());
        return Stream.concat(
                        requests.stream().map(CancellationResponse::from),
                        bookings.findCancelledBookings().stream()
                                .filter(booking -> !approvedBookingIds.contains(booking.getId()))
                                .map(CancellationResponse::fromAdminCancellation))
                .sorted(Comparator.comparing(CancellationResponse::createdAt).reversed())
                .toList();
    }

    @Transactional
    public CancellationResponse reviewCancellation(Long id, boolean approve, String reason) {
        BookingCancellationRequest request = cancellations.findById(id).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "CANCELLATION_NOT_FOUND", "Không tìm thấy yêu cầu hủy"));
        if (!request.getStatus().equals("PENDING"))
            throw conflict("ALREADY_REVIEWED", "Yêu cầu đã được xử lý");
        Booking booking = request.getBooking();
        if (approve) {
            courts.lock(booking.getCourt().getId());
            if (!List.of("HOLD", "CONFIRMED").contains(booking.getStatus()))
                throw conflict("BOOKING_NOT_CANCELLABLE", "Phiếu không còn ở trạng thái có thể hủy");
            booking.cancel(reason);
            paymentLinks.cancelForBooking(booking.getId());
            audit.record("BOOKING_CANCELLATION_APPROVED", "BOOKING", booking.getId(), reason);
        }
        request.review(null, approve ? "APPROVED" : "REJECTED", reason);
        return CancellationResponse.from(request);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> adminList() {
        return bookings.findAllVisibleToAdmin().stream()
                .map(booking -> BookingResponse.from(booking, null)).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse adminDetail(Long id) { return BookingResponse.from(byId(id), null); }

    @Transactional
    public BookingResponse adminCancel(Long id, String reason) {
        Booking booking = byId(id);
        courts.lock(booking.getCourt().getId());
        if (!List.of("HOLD", "CONFIRMED").contains(booking.getStatus())) {
            throw conflict("BOOKING_NOT_CANCELLABLE", "Không thể hủy phiếu ở trạng thái này");
        }
        booking.cancel(reason);
        paymentLinks.cancelForBooking(booking.getId());
        audit.record("BOOKING_CANCELLED", "BOOKING", id, reason);
        return BookingResponse.from(booking, null);
    }

    @Transactional
    public BookingResponse checkIn(Long id) {
        Booking booking = byId(id);
        if (!booking.getStatus().equals("CONFIRMED")) {
            throw conflict("INVALID_CHECK_IN", "Chỉ nhận sân cho phiếu đã đặt cọc");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (now.isBefore(booking.getStartAt().minusMinutes(30))) {
            throw conflict("ARRIVAL_TOO_EARLY", "Chỉ có thể nhận sân từ 30 phút trước giờ bắt đầu");
        }
        if (now.isAfter(booking.getStartAt()
                .plusMinutes(booking.getLateArrivalGraceMinutesSnapshot()))) {
            throw conflict("ARRIVAL_TOO_LATE", "Quá thời gian đến trễ cho phép");
        }
        booking.checkIn();
        return BookingResponse.from(booking, null);
    }

    @Transactional
    public BookingResponse complete(Long id) {
        Booking booking = byId(id);
        if (!booking.getStatus().equals("CHECKED_IN")
                || LocalDateTime.now(ZoneOffset.UTC).isBefore(booking.getEndAt())) {
            throw conflict("INVALID_COMPLETION", "Chỉ hoàn tất sau giờ chơi của phiếu đã nhận sân");
        }
        booking.complete();
        return BookingResponse.from(booking, null);
    }

    @Transactional
    public BookingResponse noShow(Long id) {
        Booking booking = byId(id);
        if (!booking.getStatus().equals("CONFIRMED")
                || LocalDateTime.now(ZoneOffset.UTC).isBefore(booking.getStartAt()
                        .plusMinutes(booking.getLateArrivalGraceMinutesSnapshot()))) {
            throw conflict("NO_SHOW_TOO_EARLY", "Chưa đủ 30 phút sau giờ bắt đầu hoặc khách đã nhận sân");
        }
        booking.markNoShow();
        audit.record("BOOKING_NO_SHOW", "BOOKING", id, "Quá hạn đến sân");
        return BookingResponse.from(booking, null);
    }

    @Transactional
    public void confirmPaidDeposit(Long bookingId) {
        Booking booking = byId(bookingId);
        courts.lock(booking.getCourt().getId());
        if (!booking.getStatus().equals("HOLD")
                || !booking.getHoldExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw conflict("HOLD_EXPIRED", "Giữ sân đã hết hạn; giao dịch cần đối soát/hoàn");
        }
        booking.confirm();
    }

    @Transactional
    public int expireHolds() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<Booking> expired = bookings.findByStatusAndHoldExpiresAtBefore("HOLD", now);
        for (Booking booking : expired) {
            courts.lock(booking.getCourt().getId());
            if (booking.getStatus().equals("HOLD") && booking.getHoldExpiresAt().isBefore(now)) {
                booking.expire();
                paymentLinks.cancelForBooking(booking.getId());
            }
        }
        return expired.size();
    }

    public Booking byCode(String code) {
        return bookings.findByBookingCode(code).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Không tìm thấy phiếu"));
    }

    public Booking byId(Long id) {
        return bookings.findById(id).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Không tìm thấy phiếu"));
    }

    private BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }
}
