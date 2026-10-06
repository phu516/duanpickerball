package com.pickleball.court.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "court_blocks")
public class CourtBlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id") private Court court;
    @Column(nullable = false) private LocalDateTime startAt;
    @Column(nullable = false) private LocalDateTime endAt;
    @Column(nullable = false) private String reason;
    @Column(nullable = false) private boolean active;
    private Long createdBy;
    @Column(nullable = false) private LocalDateTime createdAt;

    protected CourtBlock() {}

    public CourtBlock(Court court, LocalDateTime startAt, LocalDateTime endAt,
                      String reason, Long createdBy) {
        this.court = court;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reason = reason;
        this.createdBy = createdBy;
        this.active = true;
        this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Court getCourt() { return court; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public String getReason() { return reason; }
    public boolean isActive() { return active; }
    public void cancel() { active = false; }
}
