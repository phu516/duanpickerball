package com.pickleball.tournament.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pickleball.tournament.service.TournamentService;
import com.pickleball.common.exception.BusinessException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TournamentController {
    private static final Pattern PHONE = Pattern.compile("[0-9+() .-]{9,16}");
    private static final Set<String> CATEGORIES = Set.of("Đơn nam", "Đơn nữ", "Đôi nam", "Đôi nữ", "Đôi nam nữ");
    private final JdbcTemplate jdbc;
    private final TournamentService tournaments;

    public TournamentController(JdbcTemplate jdbc, TournamentService tournaments) {
        this.jdbc = jdbc;
        this.tournaments = tournaments;
    }
    @GetMapping("/admin/tournament")
    public TournamentService.Schedule adminSchedule() { return tournaments.schedule(); }

    @PutMapping("/admin/tournament")
    public TournamentService.Schedule saveSchedule(@RequestBody ScheduleRequest request) {
        return tournaments.save(request.startsOn(), request.startsTime(), request.endsOn(), request.endsTime(), true);
    }

    @PostMapping("/admin/tournament/cancel")
    public TournamentService.Schedule cancelTournament() {
        return tournaments.cancel();
    }

    @GetMapping("/tournament/registration-status")
    public RegistrationStatus registrationStatus() {
        return new RegistrationStatus(tournaments.registrationOpen());
    }

    @GetMapping("/tournament/schedule")
    public PublicSchedule publicSchedule() {
        TournamentService.Schedule schedule = tournaments.schedule();
        if (schedule == null || !schedule.enabled() || "completed".equals(schedule.status())) return null;
        return new PublicSchedule(schedule.startsOn(), schedule.startsTime(), schedule.endsOn(), schedule.endsTime());
    }

    @PostMapping("/tournament/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public Registration register(@RequestBody RegistrationRequest request) {
        if (!tournaments.registrationOpen()) {
            throw new BusinessException(HttpStatus.CONFLICT, "REGISTRATION_CLOSED",
                    "Hiện không mở đăng ký giải đấu.");
        }
        String name = clean(request.fullName());
        String phone = clean(request.phone());
        String email = clean(request.email());
        String category = clean(request.category());
        String note = clean(request.note());
        if (name == null || name.length() > 120) throw new IllegalArgumentException("Vui lòng nhập họ tên (tối đa 120 ký tự).");
        if (phone == null || !PHONE.matcher(phone).matches()) throw new IllegalArgumentException("Số điện thoại không hợp lệ.");
        if (email != null && (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")))
            throw new IllegalArgumentException("Email không hợp lệ.");
        if (!CATEGORIES.contains(category)) throw new IllegalArgumentException("Vui lòng chọn hạng mục tham gia hợp lệ.");
        if (note != null && note.length() > 500) throw new IllegalArgumentException("Ghi chú tối đa 500 ký tự.");

        KeyHolder keys = new GeneratedKeyHolder();
        LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
        PreparedStatementCreator statement = connection -> {
            var prepared = connection.prepareStatement("insert into tournament_registrations (full_name, phone, email, category, note, created_at) values (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            prepared.setString(1, name);
            prepared.setString(2, phone);
            prepared.setString(3, email);
            prepared.setString(4, category);
            prepared.setString(5, note);
            prepared.setObject(6, createdAt);
            return prepared;
        };
        jdbc.update(statement, keys);
        Long id = keys.getKey().longValue();
        return registration(id);
    }

    @GetMapping("/admin/tournament/registrations")
    public List<Registration> registrations() {
        return jdbc.query("select id, full_name, phone, email, category, note, created_at from tournament_registrations order by created_at desc, id desc",
                (rs, row) -> new Registration(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getObject(7, LocalDateTime.class)));
    }

    private Registration registration(Long id) {
        return jdbc.query("select id, full_name, phone, email, category, note, created_at from tournament_registrations where id=?",
                rs -> rs.next() ? new Registration(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getObject(7, LocalDateTime.class)) : null, id);
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    // Saving activates the schedule; cancellation has its own endpoint. Accept
    // older clients' enabled field without requiring it from the date-only form.
    @JsonIgnoreProperties("enabled")
    public record ScheduleRequest(LocalDate startsOn, LocalTime startsTime, LocalDate endsOn, LocalTime endsTime) {}
    public record RegistrationStatus(boolean open) {}
    public record PublicSchedule(LocalDate startsOn, LocalTime startsTime, LocalDate endsOn, LocalTime endsTime) {}
    public record RegistrationRequest(String fullName, String phone, String email, String category, String note) {}
    public record Registration(Long id, String fullName, String phone, String email,
                               String category, String note, LocalDateTime createdAt) {}
}
