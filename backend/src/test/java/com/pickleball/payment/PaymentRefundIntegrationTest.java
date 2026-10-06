package com.pickleball.payment;

import com.pickleball.booking.dto.BookingRequest;
import com.pickleball.booking.dto.BookingResponse;
import com.pickleball.booking.service.BookingService;
import com.pickleball.common.exception.BusinessException;
import com.pickleball.court.entity.Court;
import com.pickleball.court.entity.CourtPriceRule;
import com.pickleball.court.repository.CourtPriceRuleRepository;
import com.pickleball.court.repository.CourtRepository;
import com.pickleball.payment.dto.PaymentResponse;
import com.pickleball.payment.entity.Refund;
import com.pickleball.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PaymentRefundIntegrationTest {
    @Autowired CourtRepository courts;
    @Autowired CourtPriceRuleRepository rules;
    @Autowired BookingService bookings;
    @Autowired PaymentService payments;

    @Test
    void manualDepositAndRefundHaveSeparateAuditableStates() {
        Court court = courts.save(new Court("R" + UUID.randomUUID().toString().substring(0, 8),
                "Sân hoàn tiền", "Acrylic", false, ""));
        for (int day = 1; day <= 7; day++) rules.save(new CourtPriceRule(court, day,
                LocalTime.of(6, 0), LocalTime.of(23, 0), BigDecimal.valueOf(100_000),
                LocalDate.now().minusDays(1), null));
        var from = LocalDate.now().plusDays(3).atTime(17, 0).atOffset(ZoneOffset.ofHours(7));
        BookingResponse booking = bookings.create(new BookingRequest(court.getId(),
                "Khách hoàn tiền", "0901234567", null, null, from, from.plusHours(2)));
        PaymentResponse payment = payments.manual(booking.id(), null, "DEPOSIT",
                booking.depositRequired(), "CASH", "receipt-test");
        assertEquals("SUCCEEDED", payment.status());
        assertEquals("CONFIRMED", bookings.adminDetail(booking.id()).status());

        Refund refund = payments.refund(payment.paymentId(), BigDecimal.valueOf(20_000), "Khách hủy một phần");
        assertEquals("PENDING", refund.getStatus());
        assertEquals("SUCCEEDED", payments.detail(payment.paymentId()).status());
        assertEquals("SUCCEEDED", payments.confirmManualRefund(refund.getId(), "refund-receipt").getStatus());
        assertThrows(BusinessException.class, () -> payments.refund(payment.paymentId(),
                booking.depositRequired(), "Vượt quá số thu"));
        assertEquals(1, payments.bookingRefunds(booking.bookingCode(), booking.guestAccessToken()).size());
    }
}
