package com.pickleball.common.idempotency;

import com.pickleball.common.exception.BusinessException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {
    private final IdempotencyRepository records;
    private final ObjectMapper mapper;
    public IdempotencyService(IdempotencyRepository records, ObjectMapper mapper) {
        this.records = records; this.mapper = mapper;
    }
    public Optional<Long> previous(String scope, String key, Object request) {
        if (key == null || key.isBlank()) return Optional.empty();
        validate(key);
        return records.findByScopeKeyAndRequestKey(scope, key).map(record -> {
            if (!record.getRequestHash().equals(hash(mapper.writeValueAsString(request))))
                throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "Mã yêu cầu đã được dùng cho nội dung khác");
            return record.getResultId();
        });
    }
    public void remember(String scope, String key, Object request, String type, Long id) {
        if (key == null || key.isBlank()) return;
        validate(key);
        records.saveAndFlush(new IdempotencyRecord(scope, key,
                hash(mapper.writeValueAsString(request)), type, id));
    }
    private void validate(String key) {
        if (!key.matches("[A-Za-z0-9_-]{16,120}"))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Mã yêu cầu cần có 16–120 ký tự ngẫu nhiên");
    }
    public String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
