package com.pickleball.tournament;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pickleball.tournament.service.TournamentService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import com.pickleball.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class TournamentServiceTest {
    private JdbcTemplate jdbc;
    private TournamentService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(ResultSetExtractor.class))).thenReturn(null);
        service = new TournamentService(jdbc);
    }

    @Test
    void rejectsEnabledTournamentStartingBeforeVietnameseToday() {
        LocalDate yesterday = LocalDate.now(TournamentService.LOCAL_ZONE).minusDays(1);

        assertThrows(IllegalArgumentException.class,
                () -> service.save(yesterday, LocalTime.NOON, yesterday.plusDays(2), LocalTime.NOON, true));
    }

    @Test
    void acceptsTournamentStartingToday() {
        LocalDate today = LocalDate.now(TournamentService.LOCAL_ZONE).plusDays(1);

        assertDoesNotThrow(() -> service.save(today, LocalTime.of(9, 0), today.plusDays(1), LocalTime.of(18, 0), true));
    }

    @Test
    void savedScheduleIsActivatedEvenWhenLegacyFlagIsFalse() {
        LocalDate today = LocalDate.now(TournamentService.LOCAL_ZONE).plusDays(1);

        assertDoesNotThrow(() -> service.save(today, LocalTime.of(9, 0), today.plusDays(1), LocalTime.of(18, 0), false));
        verify(jdbc).update(anyString(), eq(today), eq(LocalTime.of(9, 0)), eq(today.plusDays(1)), eq(LocalTime.of(18, 0)), eq(true), any(LocalDateTime.class));
    }

    @Test
    void blocksOnlyTheConfiguredHours() {
        LocalDate day = LocalDate.now(TournamentService.LOCAL_ZONE).plusDays(1);
        when(jdbc.query(anyString(), any(ResultSetExtractor.class))).thenReturn(
                new TournamentService.Schedule(day, LocalTime.of(9, 0), day, LocalTime.of(18, 0), true, LocalDateTime.now()));
        ZonedDateTime nine = day.atTime(9, 0).atZone(TournamentService.LOCAL_ZONE);
        ZonedDateTime eighteen = day.atTime(18, 0).atZone(TournamentService.LOCAL_ZONE);

        assertDoesNotThrow(() -> service.assertBookingAllowed(nine.minusHours(3), nine.minusHours(1)));
        assertDoesNotThrow(() -> service.assertBookingAllowed(nine.minusHours(1), nine));
        BusinessException blocked = assertThrows(BusinessException.class,
                () -> service.assertBookingAllowed(nine.minusMinutes(1), nine.plusMinutes(1)));
        assertTrue(blocked.getMessage().contains("Đang có giải đấu Pickleball từ 09:00"));
        assertDoesNotThrow(() -> service.assertBookingAllowed(eighteen, eighteen.plusHours(1)));
        assertEquals(nine, service.blockedInterval(day)[0]);
        assertEquals(eighteen, service.blockedInterval(day)[1]);
    }
}
