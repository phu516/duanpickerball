package com.pickleball.setting.controller;

import com.pickleball.setting.service.BookingPolicyService;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/settings")
public class AdminSettingController {
    private final JdbcTemplate jdbc;
    private final BookingPolicyService policies;
    public AdminSettingController(JdbcTemplate jdbc, BookingPolicyService policies) {
        this.jdbc = jdbc; this.policies = policies;
    }

    @GetMapping
    public List<Setting> list() {
        return jdbc.query("select setting_key, setting_value from settings order by setting_key",
                (ResultSet rs, int row) -> new Setting(rs.getString(1), rs.getString(2)));
    }

    @PutMapping("/{key}")
    @Transactional
    public Setting put(@PathVariable String key, @RequestBody Value request) {
        policies.validate(key, request.value());
        jdbc.update("delete from settings where setting_key = ?", key);
        jdbc.update("insert into settings(setting_key, setting_value, updated_at) values(?, ?, ?)",
                key, request.value(), LocalDateTime.now(ZoneOffset.UTC));
        return new Setting(key, request.value());
    }
    public record Setting(String key, String value) {}
    public record Value(String value) {}
}
