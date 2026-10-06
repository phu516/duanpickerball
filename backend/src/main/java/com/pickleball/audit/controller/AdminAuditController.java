package com.pickleball.audit.controller;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditController {
    private final JdbcTemplate jdbc;
    public AdminAuditController(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @GetMapping public List<AuditRow> list() {
        return jdbc.query("select id, actor_id, action, resource_type, resource_id, reason, created_at from audit_logs order by id desc limit 200",
                (rs, row) -> new AuditRow(rs.getLong(1), (Long) rs.getObject(2), rs.getString(3),
                        rs.getString(4), (Long) rs.getObject(5), rs.getString(6), rs.getTimestamp(7).toLocalDateTime()));
    }
    public record AuditRow(Long id, Long actorId, String action, String resourceType,
                           Long resourceId, String reason, LocalDateTime createdAt) {}
}
