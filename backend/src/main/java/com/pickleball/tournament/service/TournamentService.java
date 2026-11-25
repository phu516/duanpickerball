package com.pickleball.tournament.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pickleball.common.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TournamentService {
    public static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");
    private final JdbcTemplate jdbc;

    public TournamentService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Schedule schedule() {
        return jdbc.query("select starts_on, starts_time, ends_on, ends_time, enabled, updated_at from tournament_settings where id=1",
                rs -> rs.next() ? new Schedule(rs.getDate(1).toLocalDate(), rs.getObject(2, LocalTime.class),
                        rs.getDate(3).toLocalDate(), rs.getObject(4, LocalTime.class),
                        rs.getBoolean(5), rs.getObject(6, LocalDateTime.class)) : null);
    }

    public Schedule save(LocalDate startsOn, LocalTime startsTime, LocalDate endsOn, LocalTime endsTime, boolean enabled) {
        if (startsOn == null || endsOn == null) {
            throw new IllegalArgumentException("Vui lòng chọn ngày khai mạc và ngày kết thúc.");
        }
        startsTime = startsTime == null ? LocalTime.MIDNIGHT : startsTime;
        endsTime = endsTime == null ? LocalTime.MAX : endsTime;
        LocalDateTime start = LocalDateTime.of(startsOn, startsTime);
        LocalDateTime end = LocalDateTime.of(endsOn, endsTime);
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian khai mạc.");
        }
        if (start.isBefore(LocalDateTime.now(LOCAL_ZONE))) {
            throw new IllegalArgumentException("Thời gian khai mạc phải ở hiện tại hoặc tương lai.");
        }
        // Saving a schedule always activates it; the legacy enabled column stays for schema compatibility.
        enabled = true;
        LocalDateTime updatedAt = LocalDateTime.now(ZoneOffset.UTC);
        if (schedule() == null) {
            jdbc.update("insert into tournament_settings (id, starts_on, starts_time, ends_on, ends_time, enabled, updated_at) values (1, ?, ?, ?, ?, ?, ?)",
                    startsOn, startsTime, endsOn, endsTime, enabled, updatedAt);
        } else {
            jdbc.update("update tournament_settings set starts_on=?, starts_time=?, ends_on=?, ends_time=?, enabled=?, updated_at=? where id=1",
                    startsOn, startsTime, endsOn, endsTime, enabled, updatedAt);
        }
        return schedule();
    }

    public Schedule cancel() {
        Schedule current = schedule();
        if (current == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "TOURNAMENT_NOT_FOUND",
                    "Chưa có lịch giải đấu để hủy.");
        }
        if (current.enabled()) {
            jdbc.update("update tournament_settings set enabled=false, updated_at=? where id=1",
                    LocalDateTime.now(ZoneOffset.UTC));
        }
        return schedule();
    }

    public boolean isBlocked(LocalDate date) {
        Schedule current = schedule();
        return current != null && current.enabled()
                && !date.isBefore(current.startsOn()) && !date.isAfter(current.endsOn());
    }

    public ZonedDateTime[] blockedInterval(LocalDate date) {
        Schedule current = schedule();
        if (current == null || !current.enabled()) return null;
        ZonedDateTime start = current.startsAt().atZone(LOCAL_ZONE);
        ZonedDateTime end = current.endsAt().atZone(LOCAL_ZONE);
        ZonedDateTime dayStart = date.atStartOfDay(LOCAL_ZONE);
        ZonedDateTime dayEnd = date.plusDays(1).atStartOfDay(LOCAL_ZONE);
        if (!start.isBefore(dayEnd) || !end.isAfter(dayStart)) return null;
        return new ZonedDateTime[] { start.isAfter(dayStart) ? start : dayStart,
                end.isBefore(dayEnd) ? end : dayEnd };
    }

    public void assertBookingAllowed(ZonedDateTime startAt, ZonedDateTime endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) return;
        Schedule current = schedule();
        if (current != null && current.enabled()
                && startAt.isBefore(current.endsAt().atZone(LOCAL_ZONE))
                && endAt.isAfter(current.startsAt().atZone(LOCAL_ZONE))) {
            throw new BusinessException(HttpStatus.CONFLICT, "TOURNAMENT_COURTS_BLOCKED",
                    "Đang có giải đấu Pickleball từ " + current.startsAt().format(DISPLAY_TIME)
                            + " đến " + current.endsAt().format(DISPLAY_TIME)
                            + ". Tất cả sân tạm khóa trong thời gian này. Vui lòng chọn khung giờ khác.");
        }
    }

    public boolean registrationOpen() {
        Schedule current = schedule();
        return current != null && current.enabled() && LocalDateTime.now(LOCAL_ZONE).isBefore(current.endsAt());
    }

    public record Schedule(LocalDate startsOn, LocalTime startsTime, LocalDate endsOn, LocalTime endsTime,
                           boolean enabled, LocalDateTime updatedAt) {
        public LocalDateTime startsAt() { return LocalDateTime.of(startsOn, startsTime); }
        public LocalDateTime endsAt() { return LocalDateTime.of(endsOn, endsTime); }
        @JsonProperty("status")
        public String status() {
            if (!enabled) return "cancelled";
            LocalDateTime now = LocalDateTime.now(LOCAL_ZONE);
            if (now.isBefore(startsAt())) return "scheduled";
            if (!now.isBefore(endsAt())) return "completed";
            return "ongoing";
        }
    }
}
