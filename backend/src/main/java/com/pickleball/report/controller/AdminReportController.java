package com.pickleball.report.controller;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {
    private final JdbcTemplate jdbc;
    public AdminReportController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return Map.of(
                "courts", count("select count(*) from courts"),
                "products", count("select count(*) from products"),
                "bookings", count("select count(*) from bookings"),
                "orders", count("select count(*) from orders"));
    }
    @GetMapping("/payments")
    public Map<String, BigDecimal> payments() {
        BigDecimal received = sum("select coalesce(sum(amount),0) from payments where status='SUCCEEDED'");
        BigDecimal refunded = sum("select coalesce(sum(amount),0) from refunds where status='SUCCEEDED'");
        return Map.of("received", received, "refunded", refunded, "net", received.subtract(refunded));
    }
    private long count(String sql) { return jdbc.queryForObject(sql, Long.class); }
    private BigDecimal sum(String sql) { return jdbc.queryForObject(sql, BigDecimal.class); }
}
