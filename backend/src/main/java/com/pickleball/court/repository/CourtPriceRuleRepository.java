package com.pickleball.court.repository;

import com.pickleball.court.entity.CourtPriceRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourtPriceRuleRepository extends JpaRepository<CourtPriceRule, Long> {
    @Query("select rule from CourtPriceRule rule where rule.court.id = :courtId")
    List<CourtPriceRule> findByCourtId(@Param("courtId") Long courtId);
}
