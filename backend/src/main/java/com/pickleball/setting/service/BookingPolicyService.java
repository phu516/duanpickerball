package com.pickleball.setting.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BookingPolicyService {
    private final JdbcTemplate jdbc;
    private final Map<String, Integer> defaults;
    public BookingPolicyService(JdbcTemplate jdbc,
            @Value("${app.booking.hold-minutes}") int hold,
            @Value("${app.booking.arrival-grace-minutes}") int arrival,
            @Value("${app.booking.minimum-minutes}") int minimum,
            @Value("${app.booking.slot-minutes}") int slot,
            @Value("${app.booking.deposit-percent}") int deposit) {
        this.jdbc = jdbc;
        defaults = Map.of("booking.hold_minutes", hold, "booking.arrival_grace_minutes", arrival,
                "booking.minimum_minutes", minimum, "booking.slot_minutes", slot,
                "booking.deposit_percent", deposit);
    }
    public int get(String key) {
        Integer fallback = defaults.get(key);
        if (fallback == null) throw new IllegalArgumentException("Không có cấu hình này");
        return jdbc.query("select setting_value from settings where setting_key=?",
                rs -> rs.next() ? Integer.parseInt(rs.getString(1)) : fallback, key);
    }
    public boolean supported(String key) { return defaults.containsKey(key); }
    public void validate(String key, String value) {
        if (!supported(key)) throw new IllegalArgumentException("Không có cấu hình này");
        int number;
        try { number = Integer.parseInt(value); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Giá trị phải là số nguyên"); }
        if (number < 1 || number > 1440 || key.equals("booking.deposit_percent") && number > 100
                || key.equals("booking.minimum_minutes") && number < 120)
            throw new IllegalArgumentException("Giá trị cấu hình ngoài phạm vi cho phép");
    }
}
