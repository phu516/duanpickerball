package com.pickleball.audit.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void record(String action, String resourceType, Long resourceId, String reason) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? null : authentication.getName();
        Long actorId = username == null ? null : jdbc.query(
                "select id from users where username = ?", rs -> rs.next() ? rs.getLong(1) : null, username);
        jdbc.update("insert into audit_logs(actor_id, action, resource_type, resource_id, reason, created_at) values(?, ?, ?, ?, ?, ?)",
                actorId, action, resourceType, resourceId, reason, LocalDateTime.now(ZoneOffset.UTC));
    }
}
