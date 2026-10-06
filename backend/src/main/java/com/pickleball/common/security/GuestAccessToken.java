package com.pickleball.common.security;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "guest_access_tokens")
public class GuestAccessToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String resourceType;
    @Column(nullable = false) private Long resourceId;
    @Column(nullable = false, unique = true) private String tokenHash;
    @Column(nullable = false) private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    @Column(nullable = false) private LocalDateTime createdAt;

    protected GuestAccessToken() {}

    public GuestAccessToken(String resourceType, Long resourceId, String tokenHash, LocalDateTime expiresAt) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }

    public boolean permits(String type, Long id, LocalDateTime now) {
        return resourceType.equals(type) && resourceId.equals(id)
                && revokedAt == null && expiresAt.isAfter(now);
    }
}
