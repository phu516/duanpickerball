package com.pickleball.court.repository;

import com.pickleball.court.entity.CourtBlock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourtBlockRepository extends JpaRepository<CourtBlock, Long> {
    List<CourtBlock> findByCourtIdAndActiveTrueAndStartAtLessThanAndEndAtGreaterThan(
            Long courtId, LocalDateTime end, LocalDateTime start);
    List<CourtBlock> findByActiveTrueOrderByStartAtAsc();
    List<CourtBlock> findByCourtIdAndActiveTrue(Long courtId);
}
