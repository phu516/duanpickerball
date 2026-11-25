package com.pickleball.court.service;

import com.pickleball.booking.repository.BookingRepository;
import com.pickleball.common.exception.BusinessException;
import com.pickleball.court.dto.BookingQuote;
import com.pickleball.court.dto.CourtResponse;
import com.pickleball.court.entity.*;
import com.pickleball.court.repository.*;
import com.pickleball.setting.service.BookingPolicyService;
import com.pickleball.tournament.service.TournamentService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourtService {
    public static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final CourtRepository courts;
    private final CourtPriceRuleRepository prices;
    private final CourtBlockRepository blocks;
    private final BookingRepository bookings;
    private final BookingPolicyService policies;
    private final TournamentService tournaments;
    private final BigDecimal depositOverrideAmount;

    public CourtService(CourtRepository courts, CourtPriceRuleRepository prices,
                        CourtBlockRepository blocks, BookingRepository bookings,
                        BookingPolicyService policies, TournamentService tournaments,
                        @Value("${app.booking.deposit-override-amount:0}") BigDecimal depositOverrideAmount) {
        this.courts = courts;
        this.prices = prices;
        this.blocks = blocks;
        this.bookings = bookings;
        this.policies = policies;
        this.tournaments = tournaments;
        this.depositOverrideAmount = depositOverrideAmount;
    }

    @Transactional(readOnly = true)
    public List<CourtResponse> publicCourts() {
        return courts.findByStatusAndLockedFalseOrderByCodeAsc("ACTIVE").stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public List<CourtResponse> adminCourts() { return courts.findAll().stream().map(this::map).toList(); }

    @Transactional(readOnly = true)
    public CourtResponse get(Long id) {
        Court court = require(id);
        if (court.isLocked()) throw new BusinessException(HttpStatus.NOT_FOUND,
                "COURT_LOCKED", "Sân hiện đang khóa");
        return map(court);
    }

    @Transactional
    public Court lock(Long id) {
        Court court = courts.lockById(id);
        if (court == null) throw new BusinessException(HttpStatus.NOT_FOUND, "COURT_NOT_FOUND", "Không tìm thấy sân");
        return court;
    }

    @Transactional(readOnly = true)
    public BookingQuote quote(Long courtId, OffsetDateTime start, OffsetDateTime end) {
        int minimumMinutes = policies.get("booking.minimum_minutes");
        int slotMinutes = policies.get("booking.slot_minutes");
        int depositPercent = policies.get("booking.deposit_percent");
        Court court = require(courtId);
        if (court.isLocked()) {
            throw new BusinessException(HttpStatus.CONFLICT, "COURT_LOCKED", "Sân hiện đang khóa, vui lòng chọn sân khác");
        }
        if (!court.getStatus().equals("ACTIVE")) {
            throw new BusinessException(HttpStatus.CONFLICT, "COURT_CLOSED", "Sân chưa hoạt động");
        }
        Instant fromInstant = start.toInstant();
        Instant toInstant = end.toInstant();
        tournaments.assertBookingAllowed(start.toZonedDateTime(), end.toZonedDateTime());
        long minutes = Duration.between(fromInstant, toInstant).toMinutes();
        if (minutes < minimumMinutes || minutes % slotMinutes != 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DURATION",
                    "Thời lượng tối thiểu 2 giờ và chọn theo bước 30 phút");
        }
        if (!fromInstant.isAfter(Instant.now())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PAST_START", "Giờ bắt đầu phải ở tương lai");
        }
        ZonedDateTime localStart = fromInstant.atZone(LOCAL_ZONE);
        ZonedDateTime localEnd = toInstant.atZone(LOCAL_ZONE);
        if (!localStart.toLocalDate().equals(localEnd.toLocalDate())
                || localStart.getMinute() % slotMinutes != 0
                || localEnd.getMinute() % slotMinutes != 0
                || localStart.toLocalTime().isBefore(LocalTime.of(6, 0))
                || localEnd.toLocalTime().isAfter(LocalTime.of(23, 0))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "OUTSIDE_OPENING_HOURS",
                    "Chọn giờ từ 06:00 đến 23:00 trong cùng ngày");
        }
        LocalDateTime fromUtc = LocalDateTime.ofInstant(fromInstant, ZoneOffset.UTC);
        LocalDateTime toUtc = LocalDateTime.ofInstant(toInstant, ZoneOffset.UTC);
        boolean reserved = bookings.findByCourtIdAndStartAtLessThanAndEndAtGreaterThan(courtId, toUtc, fromUtc)
                .stream().anyMatch(booking -> booking.blocks(LocalDateTime.now(ZoneOffset.UTC)));
        boolean blocked = !blocks.findByCourtIdAndActiveTrueAndStartAtLessThanAndEndAtGreaterThan(
                courtId, toUtc, fromUtc).isEmpty();
        if (reserved || blocked) {
            throw new BusinessException(HttpStatus.CONFLICT, "COURT_UNAVAILABLE", "Sân đã được đặt hoặc khóa trong khoảng giờ này");
        }

        List<CourtPriceRule> rules = prices.findByCourtId(courtId);
        List<BookingQuote.PriceSegment> segments = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ZonedDateTime slot = localStart; slot.isBefore(localEnd); slot = slot.plusMinutes(slotMinutes)) {
            LocalDate date = slot.toLocalDate();
            LocalTime time = slot.toLocalTime();
            List<CourtPriceRule> matches = rules.stream().filter(rule -> rule.applies(date, time)).toList();
            if (matches.size() != 1) {
                throw new BusinessException(HttpStatus.CONFLICT, "PRICE_RULE_MISSING_OR_OVERLAP",
                        "Khung giờ chưa có giá hợp lệ hoặc có quy tắc giá trùng");
            }
            BigDecimal hourly = matches.getFirst().getHourlyPrice();
            BigDecimal amount = hourly.multiply(BigDecimal.valueOf(slotMinutes))
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            total = total.add(amount);
            segments.add(new BookingQuote.PriceSegment(slot.toOffsetDateTime(),
                    slot.plusMinutes(slotMinutes).toOffsetDateTime(), hourly, amount));
        }
        BigDecimal deposit = total.multiply(BigDecimal.valueOf(depositPercent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        if (depositOverrideAmount.signum() > 0) {
            deposit = depositOverrideAmount.min(total);
        }
        return new BookingQuote(courtId, start, end, total, deposit, total.subtract(deposit), "VND", segments);
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlot> availability(LocalDate date, Long courtId) {
        List<Court> selected = courtId == null ? courts.findByStatusOrderByCodeAsc("ACTIVE") : List.of(require(courtId));
        LocalDateTime from = date.atStartOfDay(LOCAL_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime to = date.plusDays(1).atStartOfDay(LOCAL_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        List<AvailabilitySlot> result = new ArrayList<>();
        for (Court court : selected) {
            var tournamentInterval = tournaments.blockedInterval(date);
            if (tournamentInterval != null) {
                result.add(new AvailabilitySlot(court.getId(),
                        tournamentInterval[0].withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime(),
                        tournamentInterval[1].withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime(), "BLOCKED"));
            }
            if (court.isLocked()) {
                result.add(new AvailabilitySlot(court.getId(), from.atOffset(ZoneOffset.UTC),
                        to.atOffset(ZoneOffset.UTC), "BLOCKED"));
            }
            bookings.findByCourtIdAndStartAtLessThanAndEndAtGreaterThan(court.getId(), to, from)
                    .stream().filter(booking -> booking.blocks(LocalDateTime.now(ZoneOffset.UTC)))
                    .forEach(booking -> result.add(new AvailabilitySlot(court.getId(),
                            booking.getStartAt().atOffset(ZoneOffset.UTC),
                            booking.getEndAt().atOffset(ZoneOffset.UTC), "BOOKED")));
            blocks.findByCourtIdAndActiveTrueAndStartAtLessThanAndEndAtGreaterThan(court.getId(), to, from)
                    .forEach(block -> result.add(new AvailabilitySlot(court.getId(),
                            block.getStartAt().atOffset(ZoneOffset.UTC),
                            block.getEndAt().atOffset(ZoneOffset.UTC), "BLOCKED")));
        }
        return result;
    }

    @Transactional
    public CourtResponse addCourt(NewCourt request) {
        String code = request.code().trim().toUpperCase();
        if (courts.existsByCode(code)) throw new BusinessException(HttpStatus.CONFLICT,
                "COURT_CODE_EXISTS", "Mã sân đã tồn tại");
        Court court = courts.save(new Court(code, request.name(), request.surface(),
                request.indoor(), request.description()));
        return map(court);
    }

    @Transactional
    public CourtResponse updateCourt(Long id, UpdateCourt request) {
        Court court = lock(id);
        if (!request.status().equals("ACTIVE")) {
            boolean futureBooking = bookings.findByCourtIdAndStartAtLessThanAndEndAtGreaterThan(
                    id, LocalDateTime.now(ZoneOffset.UTC).plusYears(2), LocalDateTime.now(ZoneOffset.UTC))
                    .stream().anyMatch(booking -> booking.blocks(LocalDateTime.now(ZoneOffset.UTC)));
            if (futureBooking) throw new BusinessException(HttpStatus.CONFLICT,
                    "FUTURE_BOOKINGS_EXIST", "Sân còn phiếu tương lai; xử lý phiếu trước khi ngừng sân");
        }
        court.update(request.name(), request.surface(), request.indoor(),
                request.description(), request.status());
        return map(court);
    }

    @Transactional
    public CourtResponse lockCourt(Long id) {
        Court court = lock(id);
        if (court.isLocked()) return map(court);
        court.setLocked(true);
        return map(court);
    }

    @Transactional
    public CourtResponse unlockCourt(Long id) {
        Court court = lock(id);
        court.setLocked(false);
        // Clear any time-limited locks created by the retired maintenance workflow.
        blocks.findByCourtIdAndActiveTrue(id).forEach(CourtBlock::cancel);
        return map(court);
    }

    @Transactional
    public CourtPriceRule addPriceRule(Long courtId, NewPriceRule request) {
        Court court = lock(courtId);
        if (request.dayOfWeek() < 1 || request.dayOfWeek() > 7
                || !request.startTime().isBefore(request.endTime())
                || request.hourlyPrice().signum() <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PRICE_RULE", "Quy tắc giá không hợp lệ");
        }
        boolean overlaps = prices.findByCourtId(courtId).stream().anyMatch(rule ->
                rule.getDayOfWeek() == request.dayOfWeek()
                && rule.getStartTime().isBefore(request.endTime())
                && rule.getEndTime().isAfter(request.startTime())
                && (rule.getValidTo() == null || !rule.getValidTo().isBefore(request.validFrom()))
                && (request.validTo() == null || !request.validTo().isBefore(rule.getValidFrom())));
        if (overlaps) throw new BusinessException(HttpStatus.CONFLICT,
                "PRICE_RULE_OVERLAP", "Đã có giá trong ngày và khung giờ này");
        return prices.save(new CourtPriceRule(court, request.dayOfWeek(), request.startTime(),
                request.endTime(), request.hourlyPrice(), request.validFrom(), request.validTo()));
    }

    @Transactional(readOnly = true)
    public List<PriceRuleResponse> priceRules(Long courtId) {
        require(courtId);
        return prices.findByCourtId(courtId).stream()
                .sorted(Comparator.comparingInt(CourtPriceRule::getDayOfWeek)
                        .thenComparing(CourtPriceRule::getStartTime))
                .map(CourtService::priceRuleResponse).toList();
    }

    @Transactional
    public PriceRuleResponse changePriceRule(Long courtId, Long ruleId, PriceChange request) {
        Court court = lock(courtId);
        CourtPriceRule rule = prices.findById(ruleId).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "PRICE_RULE_NOT_FOUND", "Không tìm thấy quy tắc giá"));
        if (!rule.getCourtId().equals(courtId)) throw new BusinessException(HttpStatus.NOT_FOUND,
                "PRICE_RULE_NOT_FOUND", "Quy tắc giá không thuộc sân này");
        if (request.hourlyPrice() == null || request.hourlyPrice().signum() <= 0
                || request.effectiveFrom() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PRICE_CHANGE",
                    "Giá và ngày áp dụng không hợp lệ");
        }
        if (request.effectiveFrom().isBefore(rule.getValidFrom())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PRICE_CHANGE_BEFORE_RULE",
                    "Ngày áp dụng không được trước ngày bắt đầu của giá hiện tại");
        }
        if (rule.getValidTo() != null && request.effectiveFrom().isAfter(rule.getValidTo())) {
            throw new BusinessException(HttpStatus.CONFLICT, "PRICE_RULE_EXPIRED",
                    "Ngày áp dụng nằm ngoài thời hạn của quy tắc giá đã chọn");
        }

        if (request.effectiveFrom().equals(rule.getValidFrom())) {
            rule.changePrice(request.hourlyPrice());
            return priceRuleResponse(prices.save(rule));
        }

        LocalDate previousValidTo = rule.getValidTo();
        rule.closeOn(request.effectiveFrom().minusDays(1));
        prices.save(rule);
        CourtPriceRule revised = prices.save(new CourtPriceRule(court, rule.getDayOfWeek(),
                rule.getStartTime(), rule.getEndTime(), request.hourlyPrice(),
                request.effectiveFrom(), previousValidTo));
        return priceRuleResponse(revised);
    }

    private static PriceRuleResponse priceRuleResponse(CourtPriceRule rule) {
        return new PriceRuleResponse(rule.getId(), rule.getCourtId(), rule.getDayOfWeek(),
                rule.getStartTime(), rule.getEndTime(), rule.getHourlyPrice(),
                rule.getValidFrom(), rule.getValidTo());
    }

    private Court require(Long id) {
        return courts.findById(id).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "COURT_NOT_FOUND", "Không tìm thấy sân"));
    }

    private CourtResponse map(Court court) {
        BigDecimal starting = prices.findByCourtId(court.getId()).stream()
                .map(CourtPriceRule::getHourlyPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        return new CourtResponse(court.getId(), court.getCode(), court.getName(), court.getSurface(),
                court.isIndoor(), court.getDescription(), court.getStatus(), starting, court.isLocked());
    }

    public record AvailabilitySlot(Long courtId, OffsetDateTime startAt,
                                   OffsetDateTime endAt, String status) {}
    public record NewCourt(String code, String name, String surface,
                           boolean indoor, String description) {}
    public record UpdateCourt(String name, String surface, boolean indoor,
                              String description, String status) {}
    public record NewPriceRule(int dayOfWeek, LocalTime startTime, LocalTime endTime,
                               BigDecimal hourlyPrice, LocalDate validFrom, LocalDate validTo) {}
    public record PriceChange(BigDecimal hourlyPrice, LocalDate effectiveFrom) {}
    public record PriceRuleResponse(Long id, Long courtId, int dayOfWeek, LocalTime startTime,
                                    LocalTime endTime, BigDecimal hourlyPrice,
                                    LocalDate validFrom, LocalDate validTo) {}
}
