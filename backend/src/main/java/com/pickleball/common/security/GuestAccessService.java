package com.pickleball.common.security;

import com.pickleball.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GuestAccessService {
    private final SecureRandom random = new SecureRandom();
    private final GuestAccessRepository repository;
    private final int tokenHours;

    public GuestAccessService(GuestAccessRepository repository,
                              @Value("${app.guest-token-hours:168}") int tokenHours) {
        this.repository = repository;
        this.tokenHours = tokenHours;
    }

    public String issue(String type, Long id) {
        return issue(type, id, LocalDateTime.now(ZoneOffset.UTC).plusHours(tokenHours));
    }

    public String issue(String type, Long id, LocalDateTime expiresAt) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String plain = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new GuestAccessToken(type, id, hash(plain), expiresAt));
        return plain;
    }

    public void require(String token, String type, Long id) {
        if (token == null || token.isBlank()) {
            throw forbidden();
        }
        boolean allowed = repository.findByTokenHash(hash(token))
                .map(access -> access.permits(type, id, LocalDateTime.now(ZoneOffset.UTC)))
                .orElse(false);
        if (!allowed) throw forbidden();
    }

    private BusinessException forbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN, "GUEST_ACCESS_DENIED",
                "Quyền truy cập phiếu hoặc đơn hàng không hợp lệ");
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
