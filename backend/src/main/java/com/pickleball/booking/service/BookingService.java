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
import java.time.*;
import java.util.List;
import java.util.UUID;
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

    public BookingService(BookingRepository bookings, BookingCancellationRepository cancellations,
                          CourtService courts, GuestAccessService guestAccess, ObjectMapper mapper,
                          BookingPolicyService policies, AuditService audit,
                          IdempotencyService idempotency) {
        this.bookings = bookings;
        this.cancellations = cancellations;
        this.courts = courts;
        this.guestAccess = guestAccess;
        this.mapper = mapper;
        this.policies = policies;
        this.audit = audit;
        this.idempotency = idempotency;
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

    @Transactional
    public BookingResponse cancelHold(String code, String token) {
        Booking booking = byCode(code);
        guestAccess.require(token, "BOOKING", booking.getId());
        courts.lock(booking.getCourt().getId());
        if (!booking.getStatus().equals("HOLD")) {
            throw conflict("BOOKING_NOT_ON_HOLD", "Chỉ có thể tự hủy phiếu chưa đặt cọc");
        }
        booking.cancel("Khách hủy trước khi đặt cọc");
        return BookingResponse.from(booking, null);
    }

    @Transactional
    public CancellationResponse requestCancellation(String code, String token, String reason) {
        Booking booking = byCode(code);
        guestAccess.require(token, "BOOKING", booking.getId());
        if (!booking.getStatus().equals("CONFIRMED")) {
            throw conflict("INVALID_CANCELLATION_STATE", "Phiếu chưa ở trạng thái xác nhận");
        }
        return CancellationResponse.from(cancellations.save(new BookingCancellationRequest(booking, reason)));
    }

    @Transactional(readOnly = true)
    public List<CancellationResponse> guestCancellationRequests(String code, String token) {
        Booking booking = byCode(code);
        guestAccess.require(token, "BOOKING", booking.getId());
        return cancellations.findByBookingId(booking.getId()).stream().map(CancellationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CancellationResponse> adminCancellationRequests() {
        return cancellations.findAll().stream().map(CancellationResponse::from).toList();
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
            if (!booking.getStatus().equals("CONFIRMED"))
                throw conflict("BOOKING_NOT_CANCELLABLE", "Phiếu không còn ở trạng thái xác nhận");
            booking.cancel(reason);
            audit.record("BOOKING_CANCELLATION_APPROVED", "BOOKING", booking.getId(), reason);
        }
        request.review(null, approve ? "APPROVED" : "REJECTED", reason);
        return CancellationResponse.from(request);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> adminList() {
        return bookings.findAll().stream().map(booking -> BookingResponse.from(booking, null)).toList();
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
        audit.record("BOOKING_CANCELLED", "BOOKING", id, reason);
        return BookingResponse.from(booking, null);
    }

    @Transactional
    public BookingResponse checkIn(Long id) {
        Booking booking = byId(id);
        if (!booking.getStatus().equals("CONFIRMED")) {
            throw conflict("INVALID_CHECK_IN", "Chỉ nhận sân cho phiếu đã đặt cọc");
        }
        if (LocalDateTime.now(ZoneOffset.UTC).isAfter(booking.getStartAt()
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
            if (booking.getStatus().equals("HOLD") && booking.getHoldExpiresAt().isBefore(now)) booking.expire();
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
