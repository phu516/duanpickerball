package com.pickleball.court.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "court_price_rules")
public class CourtPriceRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id") private Court court;
    @Column(nullable = false) private int dayOfWeek;
    @Column(nullable = false) private LocalTime startTime;
    @Column(nullable = false) private LocalTime endTime;
    @Column(nullable = false) private BigDecimal hourlyPrice;
    @Column(nullable = false) private LocalDate validFrom;
    private LocalDate validTo;

    protected CourtPriceRule() {}

    public CourtPriceRule(Court court, int dayOfWeek, LocalTime startTime, LocalTime endTime,
                          BigDecimal hourlyPrice, LocalDate validFrom, LocalDate validTo) {
        this.court = court;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.hourlyPrice = hourlyPrice;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public Long getId() { return id; }
    public Long getCourtId() { return court.getId(); }
    public int getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public BigDecimal getHourlyPrice() { return hourlyPrice; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidTo() { return validTo; }

    public void changePrice(BigDecimal hourlyPrice) { this.hourlyPrice = hourlyPrice; }
    public void closeOn(LocalDate validTo) { this.validTo = validTo; }

    public boolean applies(LocalDate date, LocalTime time) {
        return dayOfWeek == date.getDayOfWeek().getValue()
                && !date.isBefore(validFrom)
                && (validTo == null || !date.isAfter(validTo))
                && !time.isBefore(startTime) && time.isBefore(endTime);
    }
}
