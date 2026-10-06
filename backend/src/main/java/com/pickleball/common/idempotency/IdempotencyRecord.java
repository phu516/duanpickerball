package com.pickleball.common.idempotency;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String scopeKey;
    @Column(nullable = false) private String requestKey;
    @Column(nullable = false) private String requestHash;
    @Column(nullable = false) private String resultType;
    @Column(nullable = false) private Long resultId;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
    protected IdempotencyRecord() {}
    public IdempotencyRecord(String scopeKey, String requestKey, String requestHash,
                             String resultType, Long resultId) {
        this.scopeKey = scopeKey; this.requestKey = requestKey; this.requestHash = requestHash;
        this.resultType = resultType; this.resultId = resultId;
    }
    public String getRequestHash() { return requestHash; }
    public Long getResultId() { return resultId; }
}
